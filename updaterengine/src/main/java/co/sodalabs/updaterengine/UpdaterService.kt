package co.sodalabs.updaterengine

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import co.sodalabs.updaterengine.extension.ensureNotMainThread
import co.sodalabs.updaterengine.extension.getIndicesToRemove
import co.sodalabs.updaterengine.extension.toBoolean
import co.sodalabs.updaterengine.extension.toInt
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import co.sodalabs.updaterengine.utils.ScheduleUtils
import com.jakewharton.rxrelay2.PublishRelay
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.android.AndroidInjection
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.internal.schedulers.SingleScheduler
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "updater_engine"
private const val NOTIFICATION_ID = 20190802

private const val LENGTH_10M_MILLIS = 10L * 60 * 1000

private const val CACHE_KEY_DOWNLOADED_UPDATES = "downloaded_updates"

private const val TOTAL_CHECK_ATTEMPTS_PER_SESSION = 200
private const val TOTAL_DOWNLOAD_ATTEMPTS_PER_SESSION = 200

class UpdaterService : Service() {

    companion object {

        @JvmStatic
        private val uiHandler = Handler(Looper.getMainLooper())

        /**
         * Start the actual work such as recurring heartbeat, unfinished installs,
         * and schedule the pending check.
         */
        fun start(
            context: Context
        ) {
            uiHandler.post {
                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.action = IntentActions.ACTION_ENGINE_START
                context.startService(serviceIntent)
            }
        }

        /**
         * Do a check and force a new session at check state.
         */
        fun checkUpdateNow(
            context: Context
        ) {
            // Cancel pending jobs immediately.
            cancelPendingCheck(context)
            // Start Service with action on next main thread execution.
            uiHandler.post {
                Timber.v("[Updater] Do an immediate check now!")
                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATE
                    // Can't put boolean cause persistable bundle doesn't support
                    // boolean under 22.
                    putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, true.toInt())
                }
                context.startService(serviceIntent)
            }
        }

        private fun cancelPendingCheck(
            context: Context
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Updater] (< 21) Cancel a pending check, using AlarmManager")

                val intent = Intent(context, UpdaterService::class.java)
                intent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATE
                }

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
            } else {
                Timber.v("[Updater] (>= 21) Cancel a pending check, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                // Note: The job would be consumed by CheckJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(UpdaterJobs.JOB_ID_ENGINE_TRANSITION_TO_CHECK)
            }
        }

        /**
         * The method for the updater engine knows the update check finishes and
         * to move on. The component is responsible for the check should call this
         * method when the check completes.
         */
        fun notifyAppUpdateFound(
            context: Context,
            updates: List<AppUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Check] Check job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateFound(action, updates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateFound(action, updates, errors)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the update check finishes and
         * to move on. The component is responsible for the check should call this
         * method when the check completes.
         */
        fun notifyFirmwareUpdateFound(
            context: Context,
            updates: List<FirmwareUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Check] Check job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateFound(action, updates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateFound(action, updates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun <T : Parcelable> Intent.prepareUpdateFound(
            intentAction: String,
            updates: List<T>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = intentAction
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))
                // Error
                if (errors.isNotEmpty()) {
                    putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
                }
            }
        }

        /**
         * The method for the updater engine knows the download finishes and
         * to move on. The component responsible for download should call this
         * method when the download completes.
         */
        fun notifyAppUpdateDownloaded(
            context: Context,
            foundUpdates: List<AppUpdate>,
            downloadedUpdates: List<DownloadedAppUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Download] Download job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateDownloaded(action, foundUpdates, downloadedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateDownloaded(action, foundUpdates, downloadedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the download finishes and
         * to move on. The component responsible for download should call this
         * method when the download completes.
         */
        fun notifyFirmwareUpdateDownloaded(
            context: Context,
            foundUpdates: List<FirmwareUpdate>,
            downloadedUpdates: List<DownloadedFirmwareUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Download] Download job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateDownloaded(action, foundUpdates, downloadedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateDownloaded(action, foundUpdates, downloadedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun <T : Parcelable, R : Parcelable> Intent.prepareUpdateDownloaded(
            intentAction: String,
            foundUpdates: List<T>,
            downloadedUpdates: List<R>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = intentAction
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(foundUpdates))
                putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
                // Error
                if (errors.isNotEmpty()) {
                    putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
                }
            }
        }

        /**
         * The method for the updater engine knows the install finishes and
         * to move on. The component responsible for installing should call this
         * method when the install completes.
         */
        fun notifyAppUpdateInstalled(
            context: Context,
            appliedUpdates: List<AppliedUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Install] Install job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateInstalled(action, appliedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateInstalled(action, appliedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the install finishes and
         * to move on. The component responsible for installing should call this
         * method when the install completes.
         */
        fun notifyFirmwareUpdateInstalled(
            context: Context,
            appliedUpdates: List<FirmwareUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Install] Install job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateInstalled(action, appliedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateInstalled(action, appliedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun <T : Parcelable> Intent.prepareUpdateInstalled(
            intentAction: String,
            appliedUpdates: List<T>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = intentAction
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_APPLIED_UPDATES, ArrayList(appliedUpdates))
                // Error
                if (errors.isNotEmpty()) {
                    putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
                }
            }
        }
    }

    // TODO: Pull out the business logic to the testable controller!
    // TODO: 1) App update controller
    // TODO: 2) Firmware update controller

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var heartBeater: UpdaterHeartBeater
    @Inject
    lateinit var checker: UpdatesChecker
    @Inject
    lateinit var downloader: UpdatesDownloader
    @Inject
    lateinit var installer: UpdatesInstaller
    @Inject
    lateinit var jsonBuilder: Moshi
    @Inject
    lateinit var schedulers: IThreadSchedulers

    private val disposablesOnCreateDestroy = CompositeDisposable()

    override fun onCreate() {
        Timber.v("[Updater] Updater Service is online (should runs in the background as long as possible)")
        AndroidInjection.inject(this)
        super.onCreate()

        observeUpdateIntentOnEngineThread()
        observeDeviceTimeChanges()
    }

    override fun onDestroy() {
        Timber.v("[Updater] Updater Service is offline")
        disposablesOnCreateDestroy.clear()
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startForeground(NOTIFICATION_ID, UpdaterNotificationFactory.create(this, NOTIFICATION_CHANNEL_ID))

        intent?.let { updaterIntentForwarder.accept(it) }

        // Note: It's a long running foreground Service.
        return START_STICKY
    }

    // Updater State //////////////////////////////////////////////////////////

    private val updaterIntentForwarder = PublishRelay.create<Intent>().toSerialized()
    private val updaterScheduler = SingleScheduler()

    @Volatile
    private var updaterState: UpdaterState = UpdaterState.Idle
    @Volatile
    private var lastUpdaterState: UpdaterState = UpdaterState.Idle

    private fun observeUpdateIntentOnEngineThread() {
        updaterIntentForwarder
            .observeOn(updaterScheduler)
            .subscribe({ intent ->
                try {
                    when (intent.action) {
                        IntentActions.ACTION_ENGINE_START -> start()
                        // Note: The only acceptable state from startService()
                        IntentActions.ACTION_CHECK_UPDATE -> onRequestGeneralUpdateCheck(intent)
                        // App Update /////////////////////////////////////////////
                        IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE -> onAppUpdateCheckCompleteOrError(intent)
                        IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE -> onAppUpdateDownloadCompleteOrError(intent)
                        IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE -> onAppUpdateInstallCompleteOrError(intent)
                        // Firmware Update ////////////////////////////////////////
                        IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateCheckCompleteOrError(intent)
                        IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateDownloadCompleteOrError(intent)
                        IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateInstallCompleteOrError(intent)
                    }
                } catch (error: Throwable) {
                    Timber.e(error)
                }
            }, Timber::e)
            .addTo(disposablesOnCreateDestroy)
    }

    private fun start() {
        // Restore the updater state from the persistent store.
        // If it is in the INSTALL state, then install the updates from the disk cache.
        continueInstallsOrScheduleCheck()

        val heartbeatInterval = updaterConfig.heartbeatIntervalMillis
        heartBeater.scheduleRecurringHeartBeat(heartbeatInterval)
    }

    private fun transitionToIdleState() {
        transitionToState(UpdaterState.Idle)

        // Reset attempts
        checkAttempts = 0
        downloadAttempts = 0

        // Cancel pending downloads and installs.
        downloader.cancelPendingAndWipDownloads()
        installer.cancelPendingInstalls()

        // In idle state, we'll schedule a next check in terms of the config
        // interval.
        val packageNames = updaterConfig.packageNames
        val interval = updaterConfig.checkIntervalMillis
        scheduleDelayedCheck(packageNames, interval)
    }

    private fun transitionToCheckState(
        resetSession: Boolean
    ) {
        if (updaterState == UpdaterState.Idle ||
            // For admin user to reset the session.
            resetSession) {
            transitionToState(UpdaterState.Check)

            // Cancel pending downloads and installs.
            downloader.cancelPendingAndWipDownloads()
            installer.cancelPendingInstalls()

            // Then check.
            checker.checkNow()
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Check}")
        }
    }

    private fun transitionToDownloadStateForAppUpdate(
        foundUpdates: List<AppUpdate>
    ) {
        if (updaterState == UpdaterState.Check) {
            transitionToState(UpdaterState.Download)
            downloader.downloadAppUpdateNow(foundUpdates)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Download}")
        }
    }

    private fun transitionToDownloadStateForFirmwareUpdate(
        foundUpdates: List<FirmwareUpdate>
    ) {
        if (updaterState == UpdaterState.Check) {
            transitionToState(UpdaterState.Download)
            downloader.downloadFirmwareUpdateNow(foundUpdates)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Download}")
        }
    }

    private fun transitionToInstallStateForAppUpdate(
        downloadedUpdates: List<DownloadedAppUpdate>
    ) {
        if (updaterState == UpdaterState.Idle ||
            updaterState == UpdaterState.Download) {
            transitionToState(UpdaterState.Install)

            val installWindow = updaterConfig.installWindow
            val time = ScheduleUtils.findNextInstallTimeMillis(installWindow)

            installer.scheduleInstallAppUpdate(downloadedUpdates, time)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Install}")
        }
    }

    private fun transitionToInstallStateForFirmwareUpdate(
        downloadedUpdates: List<DownloadedFirmwareUpdate>
    ) {
        if (updaterState == UpdaterState.Idle ||
            updaterState == UpdaterState.Download) {
            transitionToState(UpdaterState.Install)

            val installWindow = updaterConfig.installWindow
            val time = ScheduleUtils.findNextInstallTimeMillis(installWindow)

            installer.scheduleInstallFirmwareUpdate(downloadedUpdates, time)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Install}")
        }
    }

    /**
     * Note: Only state transition function can call this method.
     */
    private fun transitionToState(
        nextState: UpdaterState
    ) {
        // For visuals of state transition, check out the link here,
        // https://www.notion.so/sodalabs/APK-Updater-Overview-a3033e1f51604668a9dae02bdb1d7d09
        Timber.v("[Updater] Transition updater state from \"$updaterState\" to \"$nextState\"")
        lastUpdaterState = updaterState
        updaterState = nextState
    }

    private fun continueInstallsOrScheduleCheck() {
        inflateUpdatesFromCache()
            .observeOn(updaterScheduler)
            .subscribe({ foundInstallCache ->
                // TODO: Should check the timestamp of the install cache to see if it's due.
                if (foundInstallCache.isNotEmpty()) {
                    Timber.v("[Updater] Found the downloaded update cache on start!")
                    transitionToInstallStateForAppUpdate(foundInstallCache)
                } else {
                    Timber.v("[Updater] Not found any downloaded update cache on start.")
                    transitionToIdleState()
                }
            }, Timber::e)
            .addTo(disposablesOnCreateDestroy)
    }

    // Disk Cache /////////////////////////////////////////////////////////////

    private fun inflateUpdatesFromCache(): Single<List<DownloadedAppUpdate>> {
        return Single
            .fromCallable {
                ensureNotMainThread()

                val diskCache = updaterConfig.downloadedUpdateDiskCache
                if (diskCache.isClosed) {
                    diskCache.open()
                }

                val record: DiskLruCache.Value? = diskCache.get(CACHE_KEY_DOWNLOADED_UPDATES)
                val originalCache = record?.let { safeRecord ->
                    val recordFile = safeRecord.getFile(0)
                    val jsonText = recordFile.readText()

                    cleanDownloadedAppUpdateCache()

                    try {
                        val jsonBuilder = jsonBuilder
                        val jsonType = Types.newParameterizedType(List::class.java, DownloadedAppUpdate::class.java)
                        val jsonAdapter = jsonBuilder.adapter<List<DownloadedAppUpdate>>(jsonType)
                        val downloadedUpdates = jsonAdapter.fromJson(jsonText)
                        downloadedUpdates?.trimGoneFiles()
                    } catch (ignored: Throwable) {
                        emptyList<DownloadedAppUpdate>()
                    }
                } ?: emptyList()
                val allowDowngrade = updaterConfig.installAllowDowngrade
                val trimmedCache = originalCache.trimByVersionCheck(packageManager, allowDowngrade)

                trimmedCache
            }
            .subscribeOn(updaterScheduler)
    }

    private fun persistDownloadedAppUpdates(
        downloadedUpdates: List<DownloadedAppUpdate>
    ) {
        if (downloadedUpdates.isNotEmpty()) {
            Timber.v("[Updater] Persist the downloaded updates")

            val jsonType = Types.newParameterizedType(List::class.java, DownloadedAppUpdate::class.java)
            val jsonAdapter = jsonBuilder.adapter<List<DownloadedAppUpdate>>(jsonType)
            val jsonText = jsonAdapter.toJson(downloadedUpdates)

            val diskCache = updaterConfig.downloadedUpdateDiskCache
            if (diskCache.isClosed) {
                diskCache.open()
            }
            val editor = diskCache.edit(CACHE_KEY_DOWNLOADED_UPDATES)
            val editorFile = editor.getFile(0)

            try {
                editorFile.writeText(jsonText)
            } catch (error: Throwable) {
                Timber.e(error)
            } finally {
                editor.commit()
            }
        }
    }

    private fun cleanDownloadedAppUpdateCache() {
        val diskCache = updaterConfig.downloadedUpdateDiskCache
        if (diskCache.isOpened) {
            Timber.v("[Updater] Remove installs cache")
            diskCache.delete()
        }
    }

    private fun List<DownloadedAppUpdate>.trimGoneFiles(): List<DownloadedAppUpdate> {
        return this.filter { it.file.exists() }
    }

    private fun List<DownloadedAppUpdate>.trimByVersionCheck(
        packageManager: PackageManager,
        allowDowngrade: Boolean
    ): List<DownloadedAppUpdate> {
        val originalUpdates = this
        val indicesToRemove = this.map { it.fromUpdate }.getIndicesToRemove(packageManager, allowDowngrade)
        val updatesToRemove = indicesToRemove.map { i -> originalUpdates[i] }

        val trimmedList = this.toMutableList()
        trimmedList.removeAll(updatesToRemove)

        return trimmedList
    }

    // General Check //////////////////////////////////////////////////////////

    private var checkAttempts: Int = 0

    private fun onRequestGeneralUpdateCheck(
        intent: Intent
    ) {
        // Can't put boolean cause persistable bundle doesn't support
        // boolean under 22.
        val resetSessionInt = intent.getIntExtra(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
        val resetSessionBoolean = resetSessionInt.toBoolean()

        transitionToCheckState(resetSessionBoolean)
    }

    // App Update /////////////////////////////////////////////////////////////

    private var downloadAttempts: Int = 0

    @Suppress("UNCHECKED_CAST")
    private fun onAppUpdateCheckCompleteOrError(
        intent: Intent
    ) {
        val updatesError: Throwable? = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable?
        updatesError?.let {
            // TODO error-handling
            Timber.e(it)

            // Fall back to idle.
            transitionToState(UpdaterState.Idle)
        } ?: kotlin.run {
            val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
            transitionToDownloadStateForAppUpdate(updates)
        }
    }

    private fun onAppUpdateDownloadCompleteOrError(
        intent: Intent
    ) {
        val foundUpdates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
        val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedAppUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? CompositeException
        nullableError?.let { compositeError ->
            // TODO: Fall back to IDLE directly when there's no internet?

            val oldAttempts = downloadAttempts
            val newAttempts = ++downloadAttempts
            if (newAttempts < TOTAL_DOWNLOAD_ATTEMPTS_PER_SESSION) {
                Timber.e("[Updater] Failed to download some of the found updates, will retry soon (there were $oldAttempts attempts)")
                Timber.e(compositeError)
                val triggerAtMillis = ScheduleUtils.findNextDownloadTimeMillis(newAttempts)

                // Retry (and stays in the same state).
                downloader.scheduleDownloadAppUpdate(foundUpdates, triggerAtMillis)
            } else {
                // Fall back to idle after all the attempts fail.
                transitionToIdleState()
            }
        } ?: kotlin.run {
            if (downloadedUpdates.isNotEmpty()) {
                // Serialize the downloaded updates on storage for the case if the
                // device reboots, we'll continue the install on boot.
                try {
                    persistDownloadedAppUpdates(downloadedUpdates)
                } catch (error: Throwable) {
                    Timber.e(error)
                }
                // Move on to installing updates.
                transitionToInstallStateForAppUpdate(downloadedUpdates)
            } else {
                // Fall back to idle when there's no downloaded update.
                transitionToIdleState()
            }
        }
    }

    private fun onAppUpdateInstallCompleteOrError(
        intent: Intent
    ) {
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? CompositeException
        nullableError?.let { error ->
            // TODO error-handling
        }

        // Clean the persistent downloaded updates.
        try {
            cleanDownloadedAppUpdateCache()
        } catch (error: Throwable) {
            Timber.e(error)
        }

        val appliedUpdates = intent.getParcelableArrayListExtra<AppliedUpdate>(IntentActions.PROP_APPLIED_UPDATES)
        // TODO: What shall we do with this info?

        // Transition to idle at the end.
        transitionToIdleState()
    }

    /**
     * Schedule a delayed check which replaces the previous pending check.
     */
    private fun scheduleDelayedCheck(
        packageNames: List<String>,
        delayMillis: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Timber.v("[Updater] (< 21) Schedule a delayed check, using AlarmManager")

            val intent = Intent(this, UpdaterService::class.java)
            intent.apply {
                action = IntentActions.ACTION_CHECK_UPDATE
                putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames.toTypedArray())
                // Can't put boolean cause persistable bundle doesn't support
                // boolean under 22.
                putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
            }

            val alarmManager = this.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            alarmManager.cancel(pendingIntent)
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + delayMillis,
                pendingIntent
            )
        } else {
            Timber.v("[Updater] (>= 21) Schedule a delayed check, using android-21 JobScheduler")

            val jobScheduler = this.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(this, UpdaterService::class.java)
            val bundle = PersistableBundle()
            bundle.apply {
                putStringArray(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames.toTypedArray())
                // Can't put boolean cause that's implemented >= 22.
                putInt(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
            }

            val builder = JobInfo.Builder(UpdaterJobs.JOB_ID_ENGINE_TRANSITION_TO_CHECK, componentName)
                .setRequiresDeviceIdle(false)
                .setMinimumLatency(delayMillis)
                .setOverrideDeadline(delayMillis + LENGTH_10M_MILLIS)
                .setExtras(bundle)

            if (Build.VERSION.SDK_INT >= 26) {
                builder.setRequiresBatteryNotLow(false)
                    .setRequiresStorageNotLow(false)
            }

            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

            // Note: The job would be consumed by CheckJobService and translated
            // to an Intent. Then the Intent is handled here in onHandleWork()!
            jobScheduler.cancel(UpdaterJobs.JOB_ID_ENGINE_TRANSITION_TO_CHECK)
            jobScheduler.schedule(builder.build())
        }
    }

    // Firmware Update ////////////////////////////////////////////////////////

    private fun onFirmwareUpdateCheckCompleteOrError(
        intent: Intent
    ) {
        val updatesError: Throwable? = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable?
        updatesError?.let {
            // TODO error-handling
            Timber.e(it)

            // Fall back to idle.
            transitionToState(UpdaterState.Idle)
        } ?: kotlin.run {
            val updates = intent.getParcelableArrayListExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATES)
            transitionToDownloadStateForFirmwareUpdate(updates)
        }
    }

    private fun onFirmwareUpdateDownloadCompleteOrError(
        intent: Intent
    ) {
        val foundUpdates = intent.getParcelableArrayListExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATES)
        val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedFirmwareUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? CompositeException
        nullableError?.let { compositeError ->
            // TODO: Fall back to IDLE directly when there's no internet?

            val oldAttempts = downloadAttempts
            val newAttempts = ++downloadAttempts
            if (newAttempts < TOTAL_DOWNLOAD_ATTEMPTS_PER_SESSION) {
                Timber.e("[Updater] Failed to download some of the found updates, will retry soon (there were $oldAttempts attempts)")
                Timber.e(compositeError)
                val triggerAtMillis = ScheduleUtils.findNextDownloadTimeMillis(newAttempts)

                // Retry (and stays in the same state).
                downloader.scheduleDownloadFirmwareUpdate(foundUpdates, triggerAtMillis)
            } else {
                // Fall back to idle after all the attempts fail.
                transitionToIdleState()
            }
        } ?: kotlin.run {
            if (downloadedUpdates.isNotEmpty()) {
                // Serialize the downloaded updates on storage for the case if the
                // device reboots, we'll continue the install on boot.
                try {
                    // FIXME: Implement the firmware update cache
                    // persistDownloadedFirmwareUpdates(downloadedUpdates)
                } catch (error: Throwable) {
                    Timber.e(error)
                }
                // Move on to installing updates.
                transitionToInstallStateForFirmwareUpdate(downloadedUpdates)
            } else {
                // Fall back to idle when there's no downloaded update.
                transitionToIdleState()
            }
        }
    }

    private fun onFirmwareUpdateInstallCompleteOrError(
        intent: Intent
    ) {
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? CompositeException
        nullableError?.let { error ->
            // TODO error-handling
        }

        // Clean the persistent downloaded updates.
        try {
            // FIXME: Implement the firmware update cache
            // cleanDownloadedFirmwareUpdateCache()
        } catch (error: Throwable) {
            Timber.e(error)
        }

        // Transition to idle at the end.
        transitionToIdleState()

        // TODO: Countdown for rebooting to recovery mode
    }

    // Time ///////////////////////////////////////////////////////////////////

    private fun observeDeviceTimeChanges() {
        // TODO: Capture time changes and adjust the schedule of check or download.
    }

    // IBinder ////////////////////////////////////////////////////////////////

    override fun onBind(intent: Intent?): IBinder? {
        return remoteBinder
    }

    private val remoteBinder = object : IUpdaterService.Stub() {
        override fun getCheckIntervalSecs(): Long {
            TODO("not implemented")
        }

        override fun setCheckIntervalSecs(intervalSecs: Long) {
            TODO("not implemented")
        }

        override fun getInstallStartHourOfDay(): Long {
            TODO("not implemented")
        }

        override fun setInstallStartHourOfDay(startHourOfDay: Int) {
            TODO("not implemented")
        }

        override fun getInstallEndHourOfDay(): Long {
            TODO("not implemented")
        }

        override fun setInstallEndHourOfDay(endHourOfDay: Int) {
            TODO("not implemented")
        }

        override fun checkFirmwareUpdateNow(callback: IFirmwareCheckCallback?) {
            TODO("not implemented")
        }

        override fun installFirmwareUpdate(callback: IFirmwareInstallCallback?) {
            TODO("not implemented")
        }
    }
}