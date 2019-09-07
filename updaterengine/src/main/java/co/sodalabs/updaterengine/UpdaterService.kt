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
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import co.sodalabs.updaterengine.extension.ensureMainThread
import co.sodalabs.updaterengine.extension.getIndicesToRemove
import co.sodalabs.updaterengine.extension.toBoolean
import co.sodalabs.updaterengine.extension.toInt
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import com.squareup.moshi.Types
import dagger.android.AndroidInjection
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "updater_engine"
private const val NOTIFICATION_ID = 20190802

private const val LENGTH_10M_MILLIS = 10L * 60 * 1000

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
        fun checkUpdatesNow(
            context: Context,
            packageNames: List<String>
        ) {
            // Cancel pending jobs immediately.
            cancelPendingCheck(context)
            // Start Service with action on next main thread execution.
            uiHandler.post {
                Timber.v("[Updater] Do an immediate check now!")
                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATES
                    putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames.toTypedArray())
                    // Can't put boolean cause persistable bundle doesn't support
                    // boolean under 22.
                    putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, true.toInt())
                }
                context.startService(serviceIntent)
            }
        }

        /**
         * Schedule a delayed check which replaces the previous pending check.
         */
        fun scheduleDelayedCheck(
            context: Context,
            packageNames: List<String>,
            delayMillis: Long
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Updater] (< 21) Schedule a delayed check, using AlarmManager")

                val intent = Intent(context, UpdaterService::class.java)
                intent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATES
                    putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames.toTypedArray())
                    // Can't put boolean cause persistable bundle doesn't support
                    // boolean under 22.
                    putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
                }

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delayMillis,
                    pendingIntent
                )
            } else {
                Timber.v("[Updater] (>= 21) Schedule a delayed check, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, UpdaterService::class.java)
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

        private fun cancelPendingCheck(
            context: Context
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Updater] (< 21) Cancel a pending check, using AlarmManager")

                val intent = Intent(context, UpdaterService::class.java)
                intent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATES
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
         * to move on. The component responsible for the check should call this
         * method when the check completes.
         */
        fun notifyUpdateCheckComplete(
            context: Context,
            updates: List<AppUpdate>,
            updatesError: Throwable?
        ) {
            Timber.v("[Check] Check job just completes")
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForCheckComplete(updates, updatesError)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareForCheckComplete(updates, updatesError)
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForCheckComplete(
            updates: List<AppUpdate>,
            updatesError: Throwable?
        ) {
            this.apply {
                action = IntentActions.ACTION_CHECK_UPDATES_COMPLETE
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))
                // Error
                updatesError?.let { error ->
                    putExtra(IntentActions.PROP_ERROR, error)
                }
            }
        }

        /**
         * The method for the updater engine knows the download finishes and
         * to move on. The component responsible for download should call this
         * method when the download completes.
         */
        fun notifyDownloadsComplete(
            context: Context,
            downloadedUpdates: List<DownloadedUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Download] Download job just completes")
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForDownloadComplete(downloadedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareForDownloadComplete(downloadedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForDownloadComplete(
            downloadedUpdates: List<DownloadedUpdate>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = IntentActions.ACTION_DOWNLOAD_UPDATES_COMPLETE
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
                // Error
                putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
            }
        }

        /**
         * The method for the updater engine knows the install finishes and
         * to move on. The component responsible for installing should call this
         * method when the install completes.
         */
        fun notifyInstallComplete(
            context: Context,
            appliedUpdates: List<AppliedUpdate>,
            errors: List<Throwable>
        ) {
            Timber.v("[Install] Install job just completes")
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForInstallComplete(appliedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareForInstallComplete(appliedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForInstallComplete(
            appliedUpdates: List<AppliedUpdate>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = IntentActions.ACTION_INSTALL_UPDATES_COMPLETE
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_APPLIED_UPDATES, ArrayList(appliedUpdates))
                // Error
                putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
            }
        }
    }

    @Inject
    lateinit var schedulers: IThreadSchedulers

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        Timber.v("[Updater] Updater Service is online (should runs in the background as long as possible)")
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.v("[Updater] Updater Service is offline")
        disposables.clear()
        super.onDestroy()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startForeground(NOTIFICATION_ID, UpdaterNotificationFactory.create(this, NOTIFICATION_CHANNEL_ID))

        intent?.let { safeIntent ->
            when (safeIntent.action) {
                IntentActions.ACTION_ENGINE_START -> start()
                // Note: The only acceptable state from startService()
                IntentActions.ACTION_CHECK_UPDATES -> {
                    val packageNames = intent.getStringArrayExtra(IntentActions.PROP_APP_PACKAGE_NAMES).toList()
                    // Can't put boolean cause persistable bundle doesn't support
                    // boolean under 22.
                    val resetSessionInt = intent.getIntExtra(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
                    val resetSessionBoolean = resetSessionInt.toBoolean()
                    transitionToState(UpdaterState.Check(packageNames, resetSessionBoolean))
                }
                // The following are all for the completes
                IntentActions.ACTION_CHECK_UPDATES_COMPLETE -> onCheckComplete(safeIntent)
                IntentActions.ACTION_DOWNLOAD_UPDATES_COMPLETE -> onDownloadComplete(safeIntent)
                IntentActions.ACTION_INSTALL_UPDATES_COMPLETE -> onInstallComplete(safeIntent)
            }
        }

        // Note: It's a long running foreground Service.
        return START_STICKY
    }

    private var updaterState: UpdaterState = UpdaterState.Idle
    private var lastUpdaterState: UpdaterState = UpdaterState.Idle

    private fun transitionToState(
        nextState: UpdaterState
    ) {
        ensureMainThread()

        val canTransit = when (nextState) {
            is UpdaterState.Idle -> {
                Timber.v("[Updater] Transition updater state from \"$updaterState\" to \"$nextState\"")

                // Reset attempts
                checkAttempts = 0
                downloadAttempts = 0

                // Cancel pending downloads and installs.
                ApkUpdater.cancelPendingOrWipDownloads()
                ApkUpdater.cancelPendingInstalls()

                // In idle state, we'll schedule a next check in terms of the config
                // interval.
                val config = ApkUpdater.config()
                val packageNames = config.packageNames
                val interval = config.checkIntervalMillis
                scheduleDelayedCheck(
                    application,
                    packageNames,
                    interval
                )

                true
            }
            is UpdaterState.Check -> {
                if (updaterState is UpdaterState.Idle ||
                    // For admin user to reset the session.
                    nextState.resetSession) {
                    Timber.v("[Updater] Transition updater state from \"$updaterState\" to \"$nextState\"")

                    // Cancel pending downloads and installs.
                    ApkUpdater.cancelPendingOrWipDownloads()
                    ApkUpdater.cancelPendingInstalls()

                    // Then check.
                    ApkUpdater.checkNowViaChecker()
                    true
                } else {
                    false
                }
            }
            is UpdaterState.Download -> {
                if (updaterState is UpdaterState.Check) {
                    Timber.v("[Updater] Transition updater state from \"$updaterState\" to \"$nextState\"")
                    ApkUpdater.downloadUpdateNowViaDownloader(nextState.updates)
                    true
                } else {
                    false
                }
            }
            is UpdaterState.Install -> {
                if (updaterState is UpdaterState.Idle ||
                    updaterState is UpdaterState.Download) {
                    Timber.v("[Updater] Transition updater state from \"$updaterState\" to \"$nextState\"")
                    ApkUpdater.scheduleNextInstallViaInstaller(nextState.updates)
                    true
                } else {
                    false
                }
            }
        }

        if (canTransit) {
            lastUpdaterState = updaterState
            updaterState = nextState
        }
    }

    private var started = false

    private fun start() {
        if (started) return

        // Restore the updater state from the persistent store.
        // If it is in the INSTALL state, then install the updates from the disk cache.
        continueInstallsOrScheduleCheck()

        ApkUpdater.scheduleRecurringHeartbeat()

        started = true
    }

    private fun continueInstallsOrScheduleCheck() {
        this.inflateUpdatesFromCache()
            .observeOn(schedulers.main())
            .subscribe({ foundInstallCache ->
                // TODO: Should check the timestamp of the install cache to see if it's due.
                if (foundInstallCache.isNotEmpty()) {
                    transitionToState(UpdaterState.Install(foundInstallCache))
                } else {
                    transitionToState(UpdaterState.Idle)
                }
            }, Timber::e)
            .addTo(disposables)
    }

    // Disk Cache /////////////////////////////////////////////////////////////

    private fun inflateUpdatesFromCache(): Single<List<DownloadedUpdate>> {
        return Single
            .fromCallable {
                val diskCache = ApkUpdater.downloadedUpdateDiskCache()
                if (diskCache.isClosed) {
                    diskCache.open()
                }

                val record: DiskLruCache.Value? = diskCache.get(ApkUpdater.KEY_DOWNLOADED_UPDATES)
                val originalCache = record?.let { safeRecord ->
                    val recordFile = safeRecord.getFile(0)
                    val jsonText = recordFile.readText()

                    clearUpdatesCache()

                    try {
                        val jsonBuilder = ApkUpdater.jsonBuilder()
                        val jsonType = Types.newParameterizedType(List::class.java, DownloadedUpdate::class.java)
                        val jsonAdapter = jsonBuilder.adapter<List<DownloadedUpdate>>(jsonType)
                        val downloadedUpdates = jsonAdapter.fromJson(jsonText)
                        downloadedUpdates?.trimGoneFiles()
                    } catch (ignored: Throwable) {
                        emptyList<DownloadedUpdate>()
                    }
                } ?: emptyList()
                val allowDowngrade = ApkUpdater.installAllowDowngrade()
                val trimmedCache = originalCache.trimByVersionCheck(packageManager, allowDowngrade)

                trimmedCache
            }
            .subscribeOn(schedulers.io())
    }

    private fun clearUpdatesCache() {
        val diskCache = ApkUpdater.downloadedUpdateDiskCache()
        if (diskCache.isOpened) {
            Timber.v("[Updater] Remove installs cache")
            diskCache.delete()
        }
    }

    private fun List<DownloadedUpdate>.trimGoneFiles(): List<DownloadedUpdate> {
        return this.filter { it.file.exists() }
    }

    private fun List<DownloadedUpdate>.trimByVersionCheck(
        packageManager: PackageManager,
        allowDowngrade: Boolean
    ): List<DownloadedUpdate> {
        val originalUpdates = this
        val indicesToRemove = this.map { it.fromUpdate }.getIndicesToRemove(packageManager, allowDowngrade)
        val updatesToRemove = indicesToRemove.map { i -> originalUpdates[i] }

        val trimmedList = this.toMutableList()
        trimmedList.removeAll(updatesToRemove)

        return trimmedList
    }

    // Check, Download, Install ///////////////////////////////////////////////

    private var checkAttempts: Int = 0
    private var downloadAttempts: Int = 0

    @Suppress("UNCHECKED_CAST")
    private fun onCheckComplete(
        intent: Intent
    ) {
        val updatesError: Throwable? = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable?
        updatesError?.let {
            // TODO error-handling
            ++checkAttempts
            Timber.e(it)

            // Fall back to idle.
            transitionToState(UpdaterState.Idle)
        } ?: kotlin.run {
            val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
            transitionToState(UpdaterState.Download(updates))
        }
    }

    private fun onDownloadComplete(
        intent: Intent
    ) {
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
        nullableError?.let { error ->
            // TODO error-handling
        }

        val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
        if (downloadedUpdates.isNotEmpty()) {
            transitionToState(UpdaterState.Install(downloadedUpdates))
        } else {
            // Fall back to idle when there's no downloaded update.
            transitionToState(UpdaterState.Idle)
        }
    }

    private fun onInstallComplete(
        intent: Intent
    ) {
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
        nullableError?.let { error ->
            // TODO error-handling
        }

        val appliedUpdates = intent.getParcelableArrayListExtra<AppliedUpdate>(IntentActions.PROP_APPLIED_UPDATES)
        // TODO: What shall we do with this info?

        // Transition to idle at the end.
        transitionToState(UpdaterState.Idle)
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