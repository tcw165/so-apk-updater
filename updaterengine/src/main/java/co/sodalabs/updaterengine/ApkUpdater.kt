package co.sodalabs.updaterengine

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.annotation.Keep
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.feature.core.AppUpdaterService
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import timber.log.Timber

class ApkUpdater private constructor(
    private val application: Application,
    private val config: ApkUpdaterConfig,
    private val appUpdatesChecker: AppUpdatesChecker,
    private val appUpdatesDownloader: AppUpdatesDownloader,
    private val appUpdatesInstaller: AppUpdatesInstaller,
    private val engineHeartBeater: AppUpdaterHeartBeater,
    private val schedulers: IThreadSchedulers
) {

    @Keep
    companion object {

        @Volatile
        private var engine: ApkUpdater? = null

        @Suppress("ReplaceRangeStartEndInclusiveWithFirstLast")
        fun install(
            app: Application,
            config: ApkUpdaterConfig,
            appUpdatesChecker: AppUpdatesChecker,
            appUpdatesDownloader: AppUpdatesDownloader,
            appUpdatesInstaller: AppUpdatesInstaller,
            engineHeartBeater: AppUpdaterHeartBeater,
            schedulers: IThreadSchedulers
        ) {
            // Cancel everything regardless.
            engine?.stop()

            if (engine == null) {
                synchronized(ApkUpdater::class.java) {
                    if (engine == null) {
                        val engine = ApkUpdater(
                            app,
                            config,
                            appUpdatesChecker,
                            appUpdatesDownloader,
                            appUpdatesInstaller,
                            engineHeartBeater,
                            schedulers)
                        this.engine = engine
                        this.engine?.start(app)
                    }
                }
            }
        }

        fun installed(): Boolean {
            return synchronized(ApkUpdater::class.java) {
                engine != null
            }
        }

        fun installAllowDowngrade(): Boolean {
            return synchronized(ApkUpdater::class.java) {
                engine?.config?.installAllowDowngrade ?: false
            }
        }

        fun sendHeartBeatNow() {
            return synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    engineHeartBeater.sendHeartBeatNow()
                }
            }
        }

        fun scheduleRecurringHeartbeat() {
            return synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    val interval = config.heartBeatIntervalMs
                    engineHeartBeater.scheduleRecurringHeartBeat(interval, true)
                }
            }
        }

        /**
         * Observe heart-beat result.
         *
         * @return The Observable of HTTP status code.
         */
        fun observeHeartBeat(): Observable<Int> {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.engineHeartBeater.observeRecurringHeartBeat()
            }
        }

        fun checkForUpdatesNow() {
            synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    val packageNames = config.packageNames
                    appUpdatesChecker.checkNow(packageNames)
                }
            }
        }

        fun scheduleRecurringCheck() {
            synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    val packageNames = config.packageNames
                    val interval = config.checkIntervalMs
                    appUpdatesChecker.scheduleRecurringCheck(packageNames, interval)
                }
            }
        }

        fun downloadUpdateNow(
            updates: List<AppUpdate>
        ) {
            return synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    appUpdatesDownloader.downloadNow(updates)
                }
            }
        }

        fun scheduleInstallUpdates(
            updates: List<DownloadedUpdate>
        ) {
            synchronized(ApkUpdater::class.java) {
                engine?.apply {
                    val triggerAtMillis: Long = findNextAvailableTriggerTimeMillis(config)
                    appUpdatesInstaller.scheduleInstall(updates, triggerAtMillis)
                }
            }
        }

        fun setDownloadCacheMaxSize(
            sizeInMB: Long
        ) {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesDownloader.setDownloadCacheMaxSize(sizeInMB)
                // Request for restarting the process!
                requestRestartProcess()
            }
        }

        private fun requestRestartProcess() {
            engine?.restartRequestsRelay?.accept(Unit)
        }

        private fun findNextAvailableTriggerTimeMillis(
            config: ApkUpdaterConfig
        ): Long {
            // Convert the window to Calendar today.
            val startHour = config.installWindow.start // 03:00
            val endHour = config.installWindow.endInclusive // 08:00
            Timber.v("[Updater] The install window is [$startHour..$endHour]")

            val currentTime = Instant.now()
                .atZone(ZoneId.systemDefault())
            val hour = currentTime.hour
            return if (hour in startHour..endHour) {
                val triggerAtMillis = SystemClock.elapsedRealtime() + Intervals.DELAY_INSTALL
                Timber.v("[Updater] It's currently within the install window, will install the updates after $triggerAtMillis milliseconds")
                triggerAtMillis
            } else {
                // If the current time has past the window today, schedule for tomorrow
                // 9 am -> 3 am
                val triggerTimeTomorrow = Instant.now()
                    .atZone(ZoneId.systemDefault())
                    .withHour(startHour)
                    .plusDays(1)
                val triggerAtMillis = triggerTimeTomorrow.toInstant().toEpochMilli()
                Timber.v("[Updater] It's currently out of the install window, will install the updates at $triggerTimeTomorrow, which is $triggerAtMillis milliseconds after")
                triggerAtMillis
            }
        }

        private fun validateEngine(): ApkUpdater {
            return engine ?: throw NullPointerException("Updater engine isn't yet installed!")
        }
    }

    private val disposables = CompositeDisposable()

    private fun start(
        context: Context
    ) {
        logInitInfo()
        AppUpdaterService.start(context)
    }

    private fun stop() {
        disposables.clear()
    }

    private fun logInitInfo() {
        println("[Updater] Context file directory: ${application.applicationContext.filesDir}")
        println("[Updater] Context cache directory: ${application.applicationContext.cacheDir}")
        println("[Updater] Environment data directory: ${Environment.getDataDirectory()}")
        println("[Updater] Environment external storage directory: ${Environment.getExternalStorageDirectory()}")
        println("[Updater] Environment download cache directory: ${Environment.getDownloadCacheDirectory()}")
    }

    // Changes Requiring Reboot ///////////////////////////////////////////////

    private val restartRequestsRelay = PublishRelay.create<Unit>().toSerialized()
}