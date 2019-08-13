package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import co.sodalabs.apkupdater.di.component.AppComponent
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.di.module.AppPreferenceModule
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.SharedSettingsModule
import co.sodalabs.apkupdater.di.module.SystemPropertiesModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.feature.settings.AndroidSharedSettings
import co.sodalabs.apkupdater.feature.settings.AndroidSystemProperties
import co.sodalabs.apkupdater.utils.BugsnagTree
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.ApkUpdaterConfig
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.extension.toMilliseconds
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.jakewharton.processphoenix.ProcessPhoenix
import com.jakewharton.threetenabp.AndroidThreeTen
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DEBUG_DEVICE_ID = "660112"

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

        Timber.v("[Updater] App Version Name: ${BuildConfig.VERSION_NAME}")
        Timber.v("[Updater] App Version Code: ${BuildConfig.VERSION_CODE}")

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
        val config = Configuration(BuildConfig.BUGSNAG_API_KEY).apply {
            // Only send report for staging and release
            notifyReleaseStages = arrayOf(BuildUtils.TYPE_STAGING, BuildUtils.TYPE_RELEASE)
            releaseStage = BuildConfig.BUILD_TYPE
        }
        Bugsnag.init(this, config)
        Timber.plant(BugsnagTree())
    }

    private fun initDatetime() {
        AndroidThreeTen.init(this)
    }

    // Application Singletons /////////////////////////////////////////////////

    private val schedulers = AppThreadSchedulers()
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this@UpdaterApp) }
    private val appPreferences by lazy { AppSharedPreference(preferences) }
    private val sharedSettings by lazy { AndroidSharedSettings(contentResolver, schedulers) }
    private val systemProperties by lazy { AndroidSystemProperties() }

    private fun injectDependencies() {
        // Application singleton(s)
        appComponent = DaggerAppComponent.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .threadSchedulersModule(ThreadSchedulersModule(schedulers))
            .appPreferenceModule(AppPreferenceModule(appPreferences))
            .sharedSettingsModule(SharedSettingsModule(sharedSettings))
            .systemPropertiesModule(SystemPropertiesModule(systemProperties))
            .updaterModule(UpdaterModule(this, appPreferences, schedulers))
            .build()
        appComponent.inject(this)
    }

    // Updater Engine /////////////////////////////////////////////////////////

    private fun generateUpdaterConfig(): ApkUpdaterConfig {
        val hostPackageName = packageName
        val heartbeatInterval = appPreferences.getInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS).toMilliseconds()
        val checkInterval = appPreferences.getInt(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS).toMilliseconds()
        val downloadUseCache = appPreferences.getBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
        val installHourBegin = appPreferences.getInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
        val installHourEnd = appPreferences.getInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
        val installAllowDowngrade = appPreferences.getBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)

        return ApkUpdaterConfig(
            hostPackageName = hostPackageName,
            // packageNames = listOf(hostPackageName, *BuildUtils.PACKAGES_TO_CHECK)
            packageNames = listOf(*BuildUtils.PACKAGES_TO_CHECK),
            heartbeatIntervalMillis = heartbeatInterval,
            checkIntervalMillis = checkInterval,
            downloadUseCache = downloadUseCache,
            installWindow = IntRange(installHourBegin, installHourEnd),
            installAllowDowngrade = installAllowDowngrade
        )
    }

    // Preferences ////////////////////////////////////////////////////////////

    private fun injectDefaultPreferences() {
        // Debug device ID
        if (sharedSettings.getSecureString(SharedSettingsProps.DEVICE_ID) == null &&
            (BuildUtils.isDebug() || BuildUtils.isStaging())) {
            Timber.w("[Updater] Inject the debug device ID as \"$DEBUG_DEVICE_ID\"")
            sharedSettings.putSecureString(SharedSettingsProps.DEVICE_ID, DEBUG_DEVICE_ID)
        }
        val deviceID = sharedSettings.getSecureString(SharedSettingsProps.DEVICE_ID)
        Timber.w("[Updater] The device ID is \"$deviceID\"")

        // Network
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS)
        }
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS)
        }
        if (!appPreferences.containsKey(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS)) {
            appPreferences.putInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS)
        }

        // Heartbeat
        if (!appPreferences.containsKey(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS)) {
            appPreferences.putInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
        }

        // Check
        if (!appPreferences.containsKey(PreferenceProps.CHECK_INTERVAL_SECONDS)) {
            appPreferences.putInt(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)
        }

        // Download
        if (!appPreferences.containsKey(PreferenceProps.DOWNLOAD_USE_CACHE)) {
            appPreferences.putBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
        }

        // Install
        if (!appPreferences.containsKey(PreferenceProps.INSTALL_HOUR_BEGIN)) {
            appPreferences.putInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
        }
        if (!appPreferences.containsKey(PreferenceProps.INSTALL_HOUR_END)) {
            appPreferences.putInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
        }
        if (!appPreferences.containsKey(PreferenceProps.INSTALL_ALLOW_DOWNGRADE)) {
            appPreferences.putBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)
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