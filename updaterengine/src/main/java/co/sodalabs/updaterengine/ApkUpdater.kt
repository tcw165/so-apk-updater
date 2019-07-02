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
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ApkUpdater private constructor(
    private val application: Application,
    // FIXME: Make this private!
    private val appUpdatesChecker: AppUpdatesChecker,
    private val appUpdatesDownloader: AppUpdatesDownloader,
    private val appUpdatesInstaller: AppUpdatesInstaller,
    // TODO: Deprecate the config?
    private val config: ApkUpdaterConfig,
    private val schedulers: IThreadSchedulers
) {

    @Keep
    companion object {

        @Volatile
        private var singleton: ApkUpdater? = null

        fun install(
            app: Application,
            appUpdatesChecker: AppUpdatesChecker,
            appUpdatesDownloader: AppUpdatesDownloader,
            appUpdatesInstaller: AppUpdatesInstaller,
            config: ApkUpdaterConfig,
            schedulers: IThreadSchedulers
        ) {
            // Cancel everything regardless.
            singleton?.stop()

            if (singleton == null) {
                synchronized(ApkUpdater::class.java) {
                    if (singleton == null) {
                        val instance = ApkUpdater(
                            app,
                            appUpdatesChecker,
                            appUpdatesDownloader,
                            appUpdatesInstaller,
                            config,
                            schedulers)
                        singleton = instance
                        singleton?.start()

                        // Init the recurring update
                        instance.run(ScheduleUpdateCheck(
                            interval = config.interval,
                            periodic = true
                        ))
                    }
                }
            }
        }

        // TODO: After changing the config, restart the process!
        // fun updateConfig(config: ApkUpdaterConfig)

        fun checkForUpdatesNow() {
            synchronized(ApkUpdater::class.java) {
                singleton().run(ScheduleUpdateCheck(
                    interval = 0L,
                    periodic = false
                ))
            }
        }

        internal fun singleton(): ApkUpdater {
            synchronized(ApkUpdater::class.java) {
                return singleton ?: throw IllegalStateException("Must Initialize ApkUpdater before using singleton()")
            }
        }
    }

    private val disposables = CompositeDisposable()

    private val updaterActionRelay = PublishRelay.create<UpdaterAction>().toSerialized()
    private val cancelRelay = PublishRelay.create<Unit>().toSerialized()

    private fun start() {
        observeUpdaterAction()
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
        println("[Init] Context file directory: ${application.applicationContext.filesDir}")
        println("[Init] Context cache directory: ${application.applicationContext.cacheDir}")
        println("[Init] Environment data directory: ${Environment.getDataDirectory()}")
        println("[Init] Environment external storage directory: ${Environment.getExternalStorageDirectory()}")
        println("[Init] Environment download cache directory: ${Environment.getDownloadCacheDirectory()}")
    }

    // Updater Action /////////////////////////////////////////////////////////

    private fun observeUpdaterAction() {
        updaterActionRelay
            .debounce(Intervals.DEBOUNCE_VALUE_CHANGE, TimeUnit.MILLISECONDS, schedulers.computation())
            .flatMapMaybe {
                proceedUpdaterAction(it)
                    .takeUntil(cancelRelay.firstElement())
            }
            .smartRetryWhen(ALWAYS_RETRY, 2000L, schedulers.main()) { err ->
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
}