package co.sodalabs.updaterengine

import android.app.Application
import android.os.Environment
import androidx.annotation.Keep
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable

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
                        this.engine?.start()

                        // Schedule the recurring heartbeat
                        val interval = config.heartBeatIntervalMs
                        engineHeartBeater.scheduleRecurringHeartBeat(interval, true)
                        // Schedule the recurring check & download
                        engine.appUpdatesChecker.scheduleRecurringCheck(
                            config.packageNames,
                            config.checkIntervalMs)
                    }
                }
            }
        }

        fun installed(): Boolean {
            return synchronized(ApkUpdater::class.java) {
                engine != null
            }
        }

        fun observeRestartRequests(): Observable<Unit> {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = engine ?: throw NullPointerException("Updater engine isn't yet installed!")
                safeEngine.restartRequestsRelay.hide()
            }
        }

        /**
         * Send a heart-beat to the server immediately.
         *
         * @return The HTTP status code.
         */
        fun sendHeartBeatNow(): Single<Int> {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.engineHeartBeater.sendHeartBeatNow()
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
                val safeEngine = validateEngine()
                safeEngine.appUpdatesChecker.checkNow(safeEngine.config.packageNames)
            }
        }

        fun scheduleCheckUpdate(
            afterMs: Long
        ) {
            synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesChecker.scheduleCheckAfter(safeEngine.config.packageNames, afterMs)
            }
        }

        fun downloadUpdateNow(
            updates: List<AppUpdate>
        ) {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesDownloader.downloadNow(updates)
            }
        }

        fun scheduleDownloadUpdate(
            updates: List<AppUpdate>,
            afterMs: Long
        ) {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesDownloader.scheduleDownloadAfter(updates, afterMs)
            }
        }

        fun installDownloadedUpdatesNow(
            downloadedUpdates: List<DownloadedUpdate>
        ) {
            synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesInstaller.install(downloadedUpdates)
            }
        }

        fun setDownloadCacheMaxSize(
            sizeInMB: Long
        ) {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = validateEngine()
                safeEngine.appUpdatesDownloader.setDownloadCacheMaxSize(sizeInMB)
                // Request for restarting the process!
                safeEngine.restartRequestsRelay.accept(Unit)
            }
        }

        private fun validateEngine(): ApkUpdater {
            return engine ?: throw NullPointerException("Updater engine isn't yet installed!")
        }
    }

    private val disposables = CompositeDisposable()

    private fun start() {
        logInitInfo()
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