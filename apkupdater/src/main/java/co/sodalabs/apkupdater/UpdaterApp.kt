package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import co.sodalabs.apkupdater.data.PreferenceProps
import co.sodalabs.apkupdater.di.component.AppComponent
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.di.module.AppPreferenceModule
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.SharedSettingsModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.feature.settings.AndroidSharedSettings
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.CrashlyticsTree
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.ApkUpdaterConfig
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.extension.toMilliseconds
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.jakewharton.processphoenix.ProcessPhoenix
import com.jakewharton.threetenabp.AndroidThreeTen
import io.fabric.sdk.android.Fabric
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit
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

        Timber.v("App Version Name: ${BuildConfig.VERSION_NAME}")
        Timber.v("App Version Code: ${BuildConfig.VERSION_CODE}")

        initLogging()
        initCrashReporting()
        initDatetime()

        // Note: Injection of default preference must be prior than dependencies
        // injection!
        injectDefaultPreferences()
        injectDependencies()

        observeSystemConfigChange()

        // Install the updater engine after everything else is ready.
        ApkUpdater.install(
            app = this,
            config = generateUpdaterConfig(),
            appUpdatesChecker = appUpdatesChecker,
            appUpdatesDownloader = appUpdatesDownloader,
            appUpdatesInstaller = appUpdatesInstaller,
            engineHeartBeater = heartBeater,
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

    private fun initDatetime() {
        AndroidThreeTen.init(this)
    }

    // Application Singletons /////////////////////////////////////////////////

    private val schedulers = AppThreadSchedulers()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this@UpdaterApp) }
    private val appPreferences by lazy { AppSharedPreference(preferences) }
    private val sharedSettings by lazy { AndroidSharedSettings(contentResolver, schedulers) }

    private fun injectDependencies() {
        // Application singleton(s)
        appComponent = DaggerAppComponent.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .threadSchedulersModule(ThreadSchedulersModule(schedulers))
            .appPreferenceModule(AppPreferenceModule(appPreferences))
            .sharedSettingsModule(SharedSettingsModule(sharedSettings))
            .updaterModule(UpdaterModule(this, appPreferences, schedulers))
            .build()
        appComponent.inject(this)
    }

    // Updater Engine /////////////////////////////////////////////////////////

    private fun generateUpdaterConfig(): ApkUpdaterConfig {
        val hostPackageName = packageName
        val checkInterval = appPreferences.getInt(PreferenceProps.UPDATE_CHECK_INTERVAL_SECONDS, BuildConfig.UPDATE_CHECK_INTERVAL_SECONDS).toMilliseconds()
        val installHourBegin = appPreferences.getInt(PreferenceProps.UPDATE_INSTALL_HOUR_BEGIN, BuildConfig.UPDATE_INSTALL_HOUR_BEGIN)
        val installHourEnd = appPreferences.getInt(PreferenceProps.UPDATE_INSTALL_HOUR_END, BuildConfig.UPDATE_INSTALL_HOUR_END)
        val installAllowDowngrade = appPreferences.getBoolean(PreferenceProps.UPDATE_INSTALL_ALLOW_DOWNGRADE, BuildConfig.UPDATE_INSTALL_ALLOW_DOWNGRADE)
        val heartbeatInterval = appPreferences.getInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS).toMilliseconds()

        return ApkUpdaterConfig(
            hostPackageName = hostPackageName,
            // packageNames = listOf(hostPackageName, *BuildUtils.PACKAGES_TO_CHECK)
            packageNames = listOf(*BuildUtils.PACKAGES_TO_CHECK),
            checkIntervalMs = checkInterval,
            installWindow = IntRange(installHourBegin, installHourEnd),
            installAllowDowngrade = installAllowDowngrade,
            heartBeatIntervalMs = heartbeatInterval
        )
    }

    // Preferences ////////////////////////////////////////////////////////////

    private fun injectDefaultPreferences() {
        // Timeout
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS)
        }
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS)
        }
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS)
        }
        // Update
        if (!appPreferences.containsKey(PreferenceProps.UPDATE_CHECK_INTERVAL_SECONDS)) {
            appPreferences.putInt(PreferenceProps.UPDATE_CHECK_INTERVAL_SECONDS, BuildConfig.UPDATE_CHECK_INTERVAL_SECONDS)
        }
        if (!appPreferences.containsKey(PreferenceProps.UPDATE_INSTALL_HOUR_BEGIN)) {
            appPreferences.putInt(PreferenceProps.UPDATE_INSTALL_HOUR_BEGIN, BuildConfig.UPDATE_INSTALL_HOUR_BEGIN)
        }
        if (!appPreferences.containsKey(PreferenceProps.UPDATE_INSTALL_HOUR_END)) {
            appPreferences.putInt(PreferenceProps.UPDATE_INSTALL_HOUR_END, BuildConfig.UPDATE_INSTALL_HOUR_END)
        }
        if (!appPreferences.containsKey(PreferenceProps.UPDATE_INSTALL_ALLOW_DOWNGRADE)) {
            appPreferences.putBoolean(PreferenceProps.UPDATE_INSTALL_ALLOW_DOWNGRADE, BuildConfig.UPDATE_INSTALL_ALLOW_DOWNGRADE)
        }
        // Heartbeat
        if (!appPreferences.containsKey(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS)) {
            appPreferences.putInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun observeSystemConfigChange() {
        // Restart the process for all kinds of preference change!
        appPreferences.observeAnyChange()
            .debounce(Intervals.DEBOUNCE_VALUE_CHANGE, TimeUnit.MILLISECONDS)
            .observeOn(schedulers.main())
            .subscribe({
                Timber.v("[Updater] System configuration changes, so restart the process!")
                // Forcefully flush
                preferences.edit().commit()
                // Then restart the process
                ProcessPhoenix.triggerRebirth(this@UpdaterApp)
            }, Timber::e)
            .addTo(globalDisposables)
    }
}