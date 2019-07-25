package co.sodalabs.apkupdater

import android.content.Context
import androidx.multidex.MultiDexApplication
import co.sodalabs.apkupdater.data.PreferenceProps
import co.sodalabs.apkupdater.di.component.AppComponent
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.SharedPreferenceModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.ConfigHelper
import co.sodalabs.apkupdater.utils.CrashlyticsTree
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.jakewharton.processphoenix.ProcessPhoenix
import io.fabric.sdk.android.Fabric
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import javax.inject.Inject

class UpdaterApp : MultiDexApplication() {

    companion object {

        lateinit var appComponent: AppComponent
    }

    @Inject
    lateinit var appUpdatesChecker: AppUpdatesChecker
    @Inject
    lateinit var appUpdatesDownloader: AppUpdatesDownloader
    @Inject
    lateinit var appUpdatesInstaller: AppUpdatesInstaller
    @Inject
    lateinit var heartBeater: AppUpdaterHeartBeater

    private val globalDisposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        initLogging()

        Timber.d("App Version Name: ${BuildConfig.VERSION_NAME}")
        Timber.d("App Version Code: ${BuildConfig.VERSION_CODE}")

        initCrashReporting()
        injectDefaultPreferences()
        injectDependencies()

        observeNetworkConfigChange()

        ApkUpdater.install(
            app = this,
            config = ConfigHelper.generateDefault(this),
            appUpdatesChecker = appUpdatesChecker,
            appUpdatesDownloader = appUpdatesDownloader,
            appUpdatesInstaller = appUpdatesInstaller,
            engineHeartBeater = heartBeater,
            // TODO: Deprecate this
            schedulers = schedulers)
    }

    private fun initLogging() {
        if (BuildUtils.isDebug() || BuildUtils.isStaging()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initCrashReporting() {
        val crashlyticsCore = CrashlyticsCore.Builder()
            .disabled(BuildUtils.isDebug())
            .build()

        val crashlytics = Crashlytics.Builder()
            .core(crashlyticsCore)
            .build()

        val builder = Fabric.Builder(this)
            .kits(crashlytics)

        Fabric.with(builder.build())

        if (BuildUtils.isStaging() || BuildUtils.isRelease()) {
            Timber.plant(CrashlyticsTree())
        }
    }

    // Application Singletons /////////////////////////////////////////////////

    private val schedulers = AppThreadSchedulers()
    private val preferences = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE)
    private val appPreferences = AppSharedPreference(preferences)

    private fun injectDependencies() {
        // Application singleton(s)
        appComponent = DaggerAppComponent.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .threadSchedulersModule(ThreadSchedulersModule(schedulers))
            .sharedPreferenceModule(SharedPreferenceModule(appPreferences))
            .updaterModule(UpdaterModule(this, schedulers))
            .build()
        appComponent.inject(this)
    }

    // Preferences ////////////////////////////////////////////////////////////

    private fun injectDefaultPreferences() {
        preferences.edit()
            .putInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT, BuildConfig.CONNECT_TIMEOUT_SECONDS)
            .putInt(PreferenceProps.NETWORK_WRITE_TIMEOUT, BuildConfig.READ_TIMEOUT_SECONDS)
            .putInt(PreferenceProps.NETWORK_READ_TIMEOUT, BuildConfig.WRITE_TIMEOUT_SECONDS)
            .apply()
    }

    private fun observeNetworkConfigChange() {
        // Restart the process if the timeout is changed!
        Observable.merge(
            appPreferences.observeIntChange(PreferenceProps.NETWORK_CONNECTION_TIMEOUT, BuildConfig.CONNECT_TIMEOUT_SECONDS),
            appPreferences.observeIntChange(PreferenceProps.NETWORK_WRITE_TIMEOUT, BuildConfig.READ_TIMEOUT_SECONDS),
            appPreferences.observeIntChange(PreferenceProps.NETWORK_READ_TIMEOUT, BuildConfig.WRITE_TIMEOUT_SECONDS))
            .observeOn(schedulers.main())
            .subscribe({
                Timber.v("[Updater] Network configuration changes, so restart the process!")
                ProcessPhoenix.triggerRebirth(this@UpdaterApp)
            }, Timber::e)
            .addTo(globalDisposables)
    }
}