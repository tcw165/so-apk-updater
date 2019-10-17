package co.sodalabs.updaterengine

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PersistableBundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.AppliedUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import co.sodalabs.updaterengine.extension.ensureBackgroundThread
import co.sodalabs.updaterengine.extension.getIndicesToRemove
import co.sodalabs.updaterengine.extension.prepareError
import co.sodalabs.updaterengine.extension.prepareFirmwareUpdateCheckComplete
import co.sodalabs.updaterengine.extension.prepareFirmwareUpdateDownloadComplete
import co.sodalabs.updaterengine.extension.prepareFirmwareUpdateDownloadError
import co.sodalabs.updaterengine.extension.prepareFirmwareUpdateInstallComplete
import co.sodalabs.updaterengine.extension.prepareUpdateDownloadProgress
import co.sodalabs.updaterengine.extension.prepareUpdateDownloaded
import co.sodalabs.updaterengine.extension.prepareUpdateFound
import co.sodalabs.updaterengine.extension.prepareUpdateInstalled
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
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.addTo
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val NOTIFICATION_CHANNEL_ID = "updater_engine"
private const val NOTIFICATION_ID = 20190802

private const val LENGTH_10M_MILLIS = 10L * 60 * 1000

private const val CACHE_KEY_DOWNLOADED_UPDATES = "downloaded_updates"

private const val TOTAL_CHECK_ATTEMPTS_PER_SESSION = 200
private const val TOTAL_DOWNLOAD_ATTEMPTS_PER_SESSION = 200
private const val INVALID_PROGRESS_VALUE = -1

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
            context: Context,
            resetSession: Boolean
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
                    putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, resetSession.toInt())
                }
                context.startService(serviceIntent)
            }
        }

        /**
         * Schedule a delayed check and replaces the previous pending check with
         * the new one.
         */
        fun scheduleDelayedCheck(
            context: Context,
            delayMillis: Long
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Updater] (< 21) Schedule a delayed check, using AlarmManager")

                val intent = Intent(context, UpdaterService::class.java)
                intent.apply {
                    action = IntentActions.ACTION_CHECK_UPDATE
                    // Can't put boolean cause persistable bundle doesn't support
                    // boolean under 22.
                    putExtra(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt())
                }

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMillis,
                    pendingIntent
                )
            } else {
                Timber.v("[Updater] (>= 21) Schedule a delayed check, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, UpdaterJobService::class.java)
                val bundle = PersistableBundle()
                bundle.apply {
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
        fun notifyAppUpdateCheckComplete(
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
            update: FirmwareUpdate
        ) {
            Timber.v("[Check] Check job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareFirmwareUpdateCheckComplete(action, update)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareFirmwareUpdateCheckComplete(action, update)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the update check finishes and
         * to move on. The component is responsible for the check should call this
         * method when the check completes.
         */
        fun notifyFirmwareUpdateError(
            context: Context,
            error: Throwable
        ) {
            Timber.v("[Check] Check job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_ERROR
                val broadcastIntent = Intent()
                broadcastIntent.prepareError(action, error)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareError(action, error)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine to know that the download has downloaded
         * more of the file. The component responsible for download should call this
         * method when the download has a new chunk.
         */
        fun notifyAppUpdateDownloadProgress(
            context: Context,
            update: AppUpdate,
            percentageComplete: Int,
            currentBytes: Long,
            totalBytes: Long
        ) {
            Timber.v("[Download] Download job has progress $percentageComplete%")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_APP_UPDATE_PROGRESS
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateDownloadProgress(action, update, percentageComplete, currentBytes, totalBytes)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateDownloadProgress(action, update, percentageComplete, currentBytes, totalBytes)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine to know that the download has downloaded
         * more of the file. The component responsible for download should call this
         * method when the download has a new chunk.
         */
        fun notifyFirmwareUpdateDownloadProgress(
            context: Context,
            update: FirmwareUpdate,
            percentageComplete: Int,
            currentBytes: Long,
            totalBytes: Long
        ) {
            Timber.v("[Download] Download job has progress $percentageComplete%")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS
                val broadcastIntent = Intent()
                broadcastIntent.prepareUpdateDownloadProgress(action, update, percentageComplete, currentBytes, totalBytes)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareUpdateDownloadProgress(action, update, percentageComplete, currentBytes, totalBytes)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine to know that the download finishes and
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
        fun notifyFirmwareUpdateDownloadComplete(
            context: Context,
            foundUpdate: FirmwareUpdate,
            downloadedUpdate: DownloadedFirmwareUpdate
        ) {
            Timber.v("[Download] Download job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareFirmwareUpdateDownloadComplete(action, foundUpdate, downloadedUpdate)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareFirmwareUpdateDownloadComplete(action, foundUpdate, downloadedUpdate)
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the download finishes and
         * to move on. The component responsible for download should call this
         * method when the download completes.
         */
        fun notifyFirmwareUpdateDownloadError(
            context: Context,
            foundUpdate: FirmwareUpdate,
            error: Throwable
        ) {
            Timber.v("[Download] Download job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_ERROR
                val broadcastIntent = Intent()
                broadcastIntent.prepareFirmwareUpdateDownloadError(action, foundUpdate, error)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareFirmwareUpdateDownloadError(action, foundUpdate, error)
                context.startService(serviceIntent)
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
        fun notifyFirmwareUpdateInstallComplete(
            context: Context,
            appliedUpdate: FirmwareUpdate
        ) {
            Timber.v("[Install] Install job just completes")
            uiHandler.post {
                val action = IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE
                val broadcastIntent = Intent()
                broadcastIntent.prepareFirmwareUpdateInstallComplete(action, appliedUpdate)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, UpdaterService::class.java)
                serviceIntent.prepareFirmwareUpdateInstallComplete(action, appliedUpdate)
                context.startService(serviceIntent)
            }
        }

        /**
         * @hide
         */
        fun debugInstallFirmwareUpdate(
            context: Context,
            updateFile: File,
            isIncremental: Boolean
        ) {
            val updateTypeLiteral = if (isIncremental) "incremental" else "full"
            Timber.v("[Debug] install $updateTypeLiteral update at '$updateFile'")

            uiHandler.post {
                val serviceIntent = Intent(context, UpdaterService::class.java).apply {
                    action = DebugIntentActions.ACTION_INSTALL_FIRMWARE_UPDATE
                    val mockFoundUpdate = FirmwareUpdate(
                        version = "99.99.99",
                        isIncremental = isIncremental,
                        fileURL = "",
                        fileHash = "",
                        updateOptions = ""
                    )
                    val mockDownloadedUpdate = DownloadedFirmwareUpdate(updateFile, mockFoundUpdate)
                    putExtra(IntentActions.PROP_DOWNLOADED_UPDATE, mockDownloadedUpdate)
                }

                context.startService(serviceIntent)
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
    lateinit var rebootHelper: IRebootHelper
    @Inject
    lateinit var jsonBuilder: Moshi
    @Inject
    lateinit var sharedSettings: ISharedSettings
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
                        // App Update
                        IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE -> onAppUpdateCheckCompleteOrError(intent)
                        IntentActions.ACTION_DOWNLOAD_APP_UPDATE_PROGRESS -> onAppUpdateDownloadProgress(intent)
                        IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE -> onAppUpdateDownloadCompleteOrError(intent)
                        IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE -> onAppUpdateInstallCompleteOrError(intent)
                        // Firmware Update
                        IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateCheckComplete(intent)
                        IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_ERROR -> onFirmwareUpdateCheckError(intent)
                        IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS -> onFirmwareUpdateDownloadProgress(intent)
                        IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateDownloadComplete(intent)
                        IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_ERROR -> onFirmwareUpdateDownloadError(intent)
                        IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE -> onFirmwareUpdateInstallComplete(intent)
                        IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_ERROR -> onFirmwareUpdateInstallError(intent)
                        // Debug
                        DebugIntentActions.ACTION_INSTALL_FIRMWARE_UPDATE -> debugTransitionToInstallStateForFirmwareUpdate(intent)
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
        continueInstallsOrScheduleCheckOnStart()

        val heartbeatInterval = updaterConfig.heartbeatIntervalMillis
        heartBeater.scheduleRecurringHeartBeat(heartbeatInterval)
    }

    /**
     * Transitioning to idle state can happen anytime at any state. This
     * transitioning also cancels all the pending works and resets the internal
     * properties.
     */
    private fun transitionToIdleState() {
        transitionToState(UpdaterState.Idle)

        // Reset attempts
        checkAttempts = 0
        downloadAttempts = 0

        // Cancel pending downloads and installs.
        cancelPendingCheck(this)
        downloader.cancelPendingAndWipDownloads()
        installer.cancelPendingInstalls()
    }

    /**
     * Transitioning to idle state can happen anytime at any state. This
     * transitioning also cancels all the pending works and resets the internal
     * properties.
     * But this one schedules a delayed check after the cleanup.
     */
    private fun transitionToIdleStateAndScheduleNextCheck() {
        transitionToIdleState()

        // Schedule a next check after the interval given from the config.
        if (sharedSettings.isUserSetupComplete()) {
            val interval = updaterConfig.checkIntervalMillis
            scheduleDelayedCheck(this, interval)
        }
    }

    private fun transitionToCheckState(
        resetSession: Boolean
    ) {
        if (updaterState == UpdaterState.Idle ||
            // For admin user to reset the session.
            resetSession
        ) {
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
        foundUpdate: FirmwareUpdate
    ) {
        if (updaterState == UpdaterState.Check) {
            transitionToState(UpdaterState.Download)
            downloader.downloadFirmwareUpdateNow(foundUpdate)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Download}")
        }
    }

    private fun transitionToInstallStateForAppUpdate(
        downloadedUpdates: List<DownloadedAppUpdate>
    ) {
        if (updaterState == UpdaterState.Idle ||
            updaterState == UpdaterState.Download
        ) {
            transitionToState(UpdaterState.Install)

            val installWindow = updaterConfig.installWindow
            val time = ScheduleUtils.findNextInstallTimeMillis(installWindow)

            installer.scheduleInstallAppUpdate(downloadedUpdates, time)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Install}")
        }
    }

    private fun transitionToInstallStateForFirmwareUpdate(
        downloadedUpdate: DownloadedFirmwareUpdate
    ) {
        if (updaterState == UpdaterState.Idle ||
            updaterState == UpdaterState.Download
        ) {
            transitionToState(UpdaterState.Install)

            val installWindow = updaterConfig.installWindow
            val time = ScheduleUtils.findNextInstallTimeMillis(installWindow)

            installer.scheduleInstallFirmwareUpdate(downloadedUpdate, time)
        } else {
            throw IllegalStateException("Can't transition from $updaterState to ${UpdaterState.Install}")
        }
    }

    /**
     * Note: This might disrupt the updater state.
     */
    private fun debugTransitionToInstallStateForFirmwareUpdate(
        intent: Intent
    ) {
        // Reset attempts
        checkAttempts = 0
        downloadAttempts = 0

        // Cancel pending jobs.
        cancelPendingCheck(this)
        downloader.cancelPendingAndWipDownloads()
        installer.cancelPendingInstalls()

        transitionToState(UpdaterState.Install)

        val update = intent.getParcelableExtra<DownloadedFirmwareUpdate>(IntentActions.PROP_DOWNLOADED_UPDATE)
        val currentMillis = Instant.now()
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val triggerTime = currentMillis + 1000L
        installer.scheduleInstallFirmwareUpdate(update, triggerTime)
    }

    /**
     * Note: Only state transition function can call this method.
     */
    private fun transitionToState(
        nextState: UpdaterState
    ) {
        // For visuals of state transition, check out the link here,
        // https://www.notion.so/sodalabs/APK-Updater-Overview-a3033e1f51604668a9dae02bdb1d7d09
        Timber.v("[Updater] Transition updater state from $updaterState to $nextState")
        lastUpdaterState = updaterState
        updaterState = nextState
    }

    private fun continueInstallsOrScheduleCheckOnStart() {
        Observables.combineLatest(
            inflateUpdatesFromCache().toObservable(),
            sharedSettings.observeUserSetupComplete())
            .observeOn(updaterScheduler)
            .subscribe({ (foundInstallCache, userSetupComplete) ->
                // TODO: Should check the timestamp of the install cache to see if it's due.
                if (foundInstallCache.isNotEmpty()) {
                    Timber.v("[Updater] Found a fully downloaded update cache on start!")
                    transitionToInstallStateForAppUpdate(foundInstallCache)
                } else {
                    if (userSetupComplete) {
                        Timber.v("[Updater] Not found any fully downloaded update cache on start, with user-setup complete.")
                        transitionToIdleStateAndScheduleNextCheck()
                    } else {
                        Timber.v("[Updater] Not found any fully downloaded update cache on start, with user-setup INCOMPLETE.")
                        transitionToIdleState()
                    }
                }
            }, Timber::e)
            .addTo(disposablesOnCreateDestroy)
    }

    // Disk Cache /////////////////////////////////////////////////////////////

    /**
     * Inflate the persistable update (fully downloaded) and then delete the
     * cache file (the journal file not the download cache).
     */
    private fun inflateUpdatesFromCache(): Single<List<DownloadedAppUpdate>> {
        return Single
            .fromCallable {
                ensureBackgroundThread()

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
            val editorFile = editor.getFile()

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
            Timber.e(it)

            // TODO: Broadcast the error to the remote client.

            // Fall back to idle.
            transitionToState(UpdaterState.Idle)
        } ?: kotlin.run {
            val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
            transitionToDownloadStateForAppUpdate(updates)
        }
    }

    private fun onAppUpdateDownloadProgress(
        intent: Intent
    ) {
        val foundUpdate = intent.getParcelableExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATE)
        val downloadProgressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, INVALID_PROGRESS_VALUE)
        val downloadProgressCurrentBytes = intent.getLongExtra(IntentActions.PROP_DOWNLOAD_CURRENT_BYTES, INVALID_PROGRESS_VALUE.toLong())
        val downloadProgressTotalBytes = intent.getLongExtra(IntentActions.PROP_DOWNLOAD_TOTAL_BYTES, INVALID_PROGRESS_VALUE.toLong())

        if (foundUpdate != null) {
            // TODO: Send progress to remote
        } else {
            // Fall back to idle when there's no downloaded update.
            transitionToIdleStateAndScheduleNextCheck()
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
                transitionToIdleStateAndScheduleNextCheck()
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
                transitionToIdleStateAndScheduleNextCheck()
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
        transitionToIdleStateAndScheduleNextCheck()
    }

    // Firmware Update ////////////////////////////////////////////////////////

    private fun onFirmwareUpdateCheckComplete(
        intent: Intent
    ) {
        val updates = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
        transitionToDownloadStateForFirmwareUpdate(updates)
    }

    private fun onFirmwareUpdateCheckError(
        intent: Intent
    ) {
        val error: Throwable = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable

        // Fall back to idle.
        // transitionToState(UpdaterState.Idle)

        // TODO: Broadcast the error
    }

    private fun onFirmwareUpdateDownloadProgress(
        intent: Intent
    ) {
        val foundUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
        val downloadProgressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, INVALID_PROGRESS_VALUE)
        val downloadProgressCurrentBytes = intent.getLongExtra(IntentActions.PROP_DOWNLOAD_CURRENT_BYTES, INVALID_PROGRESS_VALUE.toLong())
        val downloadProgressTotalBytes = intent.getLongExtra(IntentActions.PROP_DOWNLOAD_TOTAL_BYTES, INVALID_PROGRESS_VALUE.toLong())

        // As the download has new progress, reset the attempts.
        downloadAttempts = 0

        if (foundUpdate != null) {
            // TODO: Send progress to remote
        } else {
            // Fall back to idle when there's no downloaded update.
            transitionToIdleStateAndScheduleNextCheck()
        }
    }

    private fun onFirmwareUpdateDownloadComplete(
        intent: Intent
    ) {
        try {
            val foundUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
            val downloadedUpdate = intent.getParcelableExtra<DownloadedFirmwareUpdate>(IntentActions.PROP_DOWNLOADED_UPDATE)

            // Serialize the downloaded updates on storage for the case if the
            // device reboots, we'll continue the install on boot.
            try {
                // FIXME: Implement the firmware update cache
                // persistDownloadedFirmwareUpdates(downloadedUpdates)
            } catch (error: Throwable) {
                Timber.e(error)
            }
            // Move on to installing updates.
            transitionToInstallStateForFirmwareUpdate(downloadedUpdate)
        } catch (error: Throwable) {
            Timber.e(error)
            // Fall back to idle when there's no downloaded update.
            transitionToIdleStateAndScheduleNextCheck()
        }
    }

    private fun onFirmwareUpdateDownloadError(
        intent: Intent
    ) {
        try {
            val foundUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
            val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable
            // TODO: Fall back to IDLE directly when there's no internet?

            val oldAttempts = downloadAttempts
            val newAttempts = ++downloadAttempts
            if (newAttempts < TOTAL_DOWNLOAD_ATTEMPTS_PER_SESSION) {
                Timber.e("[Updater] Failed to download some of the found updates, will retry soon (there were $oldAttempts attempts)")
                Timber.e(error)
                val triggerAtMillis = ScheduleUtils.findNextDownloadTimeMillis(newAttempts)

                // Retry (and stays in the same state).
                downloader.scheduleDownloadFirmwareUpdate(foundUpdate, triggerAtMillis)
            } else {
                // Fall back to idle after all the attempts fail.
                transitionToIdleStateAndScheduleNextCheck()
            }
        } catch (error: Throwable) {
            Timber.e(error)
            // Fall back to idle when there's no downloaded update.
            transitionToIdleStateAndScheduleNextCheck()
        }
    }

    private fun onFirmwareUpdateInstallComplete(
        intent: Intent
    ) {
        // Clean the persistent downloaded updates.
        try {
            // FIXME: Implement the firmware update cache
            // cleanDownloadedFirmwareUpdateCache()
        } catch (error: Throwable) {
            Timber.e(error)
        }

        // TODO: Maybe we should transition to 'REBOOTING' state
        // Transition to idle at the end regardless success or fail.
        transitionToIdleStateAndScheduleNextCheck()

        val appliedUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_APPLIED_UPDATE)
        if (appliedUpdate.isIncremental) {
            // Assume it's a silent incremental update, so reboot instantly.
            rebootHelper.rebootToRecovery()
        } else {
            // TODO: Wait in the REBOOTING state so that OOBE UI can reboot on
            // TODO: its will.
            rebootHelper.rebootToRecovery()
        }
    }

    private fun onFirmwareUpdateInstallError(
        intent: Intent
    ) {
        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
        nullableError?.let { error ->
            // TODO error-handling
        }

        // Transition to idle on fail.
        transitionToIdleState()
    }

    // Time ///////////////////////////////////////////////////////////////////

    private fun observeDeviceTimeChanges() {
        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        val timeChangedObservable = RxBroadcastReceiver.bind(this, intentFilter)
            // Wait for the date time settled.
            .debounce(Intervals.DEBOUNCE_DATETIME_CHANGE, TimeUnit.MILLISECONDS, schedulers.computation())

        // Wait for the user-setup-COMPLETE to reflect the date time change.
        Observables.combineLatest(
            timeChangedObservable,
            sharedSettings.observeUserSetupComplete())
            .observeOn(updaterScheduler)
            .subscribe({ (_, userSetupComplete) ->
                if (userSetupComplete) {
                    Timber.v("[Updater] Detect the date time change with user-setup complete!")
                    transitionToIdleStateAndScheduleNextCheck()
                } else {
                    Timber.v("[Updater] Detect the date time change with user-setup INCOMPLETE!")
                    transitionToIdleState()
                }
            }, Timber::e)
            .addTo(disposablesOnCreateDestroy)
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