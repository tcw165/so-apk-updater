package co.sodalabs.updaterengine

import android.app.Application
import android.os.Environment
import androidx.annotation.Keep
import co.sodalabs.updaterengine.UpdaterAction.DownloadUpdates
import co.sodalabs.updaterengine.UpdaterAction.InstallApps
import co.sodalabs.updaterengine.UpdaterAction.ScheduleUpdateCheck
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.smartRetryWhen
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit

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

                        // Init the recurring update
                        engine.run(ScheduleUpdateCheck(
                            interval = config.checkIntervalMs,
                            periodic = true
                        ))
                    }
                }
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
                engine?.engineHeartBeater?.sendHeartBeatNow() ?: throw NullPointerException("Updater engine isn't yet installed!")
            }
        }

        /**
         * Observe heart-beat result.
         *
         * @return The Observable of HTTP status code.
         */
        fun observeHeartBeat(): Observable<Int> {
            return synchronized(ApkUpdater::class.java) {
                engine?.engineHeartBeater?.observeRecurringHeartBeat() ?: throw NullPointerException("Updater engine isn't yet installed!")
            }
        }

        fun checkForUpdatesNow() {
            synchronized(ApkUpdater::class.java) {
                engine?.run(ScheduleUpdateCheck(
                    interval = 0L,
                    periodic = false
                ))
            }
        }

        fun downloadUpdateNow(
            updates: List<AppUpdate>
        ): Single<List<Apk>> {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = engine ?: throw NullPointerException("Updater engine isn't yet installed!")
                safeEngine.downloadUpdates(updates)
            }
        }

        fun setDownloadCacheMaxSize(
            sizeInMB: Long
        ) {
            return synchronized(ApkUpdater::class.java) {
                val safeEngine = engine ?: throw NullPointerException("Updater engine isn't yet installed!")
                safeEngine.appUpdatesDownloader.setDownloadCacheMaxSize(sizeInMB)
                // Request for restarting the process!
                safeEngine.restartRequestsRelay.accept(Unit)
            }
        }
    }

    private val disposables = CompositeDisposable()

    private val updaterActionRelay = PublishRelay.create<UpdaterAction>().toSerialized()
    private val cancelRelay = PublishRelay.create<Unit>().toSerialized()

    private fun start() {
        observeUpdateStates()
        observeHeartBeat()
        logInitInfo()
    }

    private fun stop() {
        disposables.clear()
    }

    @Suppress("unused")
    private fun cancelWhatsGoingOn() {
        cancelRelay.accept(Unit)
    }

    private fun logInitInfo() {
        println("[Updater] Context file directory: ${application.applicationContext.filesDir}")
        println("[Updater] Context cache directory: ${application.applicationContext.cacheDir}")
        println("[Updater] Environment data directory: ${Environment.getDataDirectory()}")
        println("[Updater] Environment external storage directory: ${Environment.getExternalStorageDirectory()}")
        println("[Updater] Environment download cache directory: ${Environment.getDownloadCacheDirectory()}")
    }

    // Updater Action /////////////////////////////////////////////////////////

    private fun observeUpdateStates() {
        updaterActionRelay
            .debounce(Intervals.DEBOUNCE_VALUE_CHANGE, TimeUnit.MILLISECONDS, schedulers.computation())
            .flatMapMaybe {
                proceedUpdaterAction(it)
                    .takeUntil(cancelRelay.firstElement())
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.main()) { err ->
                Timber.e(err)
                true
            }
            .observeOn(schedulers.main())
            .subscribe({ nextAction ->
                run(nextAction)
            }, Timber::e)
            .addTo(disposables)
    }

    /**
     * Proceed the [action] and come up with the new action.
     */
    private fun proceedUpdaterAction(
        action: UpdaterAction
    ): Maybe<UpdaterAction> {
        val sb = StringBuilder()
        sb.appendln(".")
        sb.appendln("[Updater] Proceed \"$action\"...")
        sb.appendln(".")
        Timber.v(sb.toString())

        return when (action) {
            is ScheduleUpdateCheck -> proceedUpdateCheck(action).toDownloadAction()
            is DownloadUpdates -> downloadUpdates(action.updates).toInstallAction()
            is InstallApps -> installUpdates(action.apps).toEmptyAction()
        }
    }

    private fun run(
        action: UpdaterAction
    ) {
        updaterActionRelay.accept(action)
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private fun observeHeartBeat() {
        val interval = config.heartBeatIntervalMs
        engineHeartBeater.schedule(interval, true)
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.main()) { true }
            .subscribe({
                // TODO: What should we do here?
            }, Timber::e)
            .addTo(disposables)
    }

    // Updates (Version) Check ////////////////////////////////////////////////

    private fun proceedUpdateCheck(
        action: ScheduleUpdateCheck
    ): Single<List<AppUpdate>> {
        return if (action.periodic) {
            // FIXME
            // appUpdatesChecker.scheduleCheck()
            Timber.e("Hey developer, update schedule is temporarily disabled!")
            Single.just(emptyList())
        } else {
            appUpdatesChecker.checkNow(config.packageNames)
        }
    }

    @Suppress("USELESS_CAST")
    private fun Single<List<AppUpdate>>.toDownloadAction(): Maybe<UpdaterAction> {
        return this.flatMapMaybe { updates ->
            if (updates.isNotEmpty()) {
                val action = DownloadUpdates(updates) as UpdaterAction
                Maybe.just(action)
            } else {
                Maybe.empty()
            }
        }
    }

    // Download ///////////////////////////////////////////////////////////////

    private fun downloadUpdates(
        updates: List<AppUpdate>
    ): Single<List<Apk>> {
        return appUpdatesDownloader.download(updates)
    }

    @Suppress("RemoveRedundantQualifierName", "USELESS_CAST")
    private fun Single<List<Apk>>.toInstallAction(): Maybe<UpdaterAction> {
        return this.flatMapMaybe { apks ->
            if (apks.isNotEmpty()) {
                val action = UpdaterAction.InstallApps(apks) as UpdaterAction
                Maybe.just(action)
            } else {
                Maybe.empty()
            }
        }
    }

    // Install ////////////////////////////////////////////////////////////////

    private fun installUpdates(
        apks: List<Apk>
    ): Completable {
        val installs = apks.map { apk ->
            appUpdatesInstaller.install(apk)
        }

        return Completable.concat(installs)
    }

    private fun Completable.toEmptyAction(): Maybe<UpdaterAction> {
        return this.toMaybe()
    }

    // Changes Requiring Reboot ///////////////////////////////////////////////

    private val restartRequestsRelay = PublishRelay.create<Unit>().toSerialized()
}