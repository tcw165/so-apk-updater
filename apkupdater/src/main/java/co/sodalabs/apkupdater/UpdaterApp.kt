package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.utils.BugsnagTree
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.ApkUpdaterConfig
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.extension.toMilliseconds
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.jakewharton.processphoenix.ProcessPhoenix
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DEBUG_DEVICE_ID = "660112"

class UpdaterApp :
    MultiDexApplication(),
    HasAndroidInjector {

    @Inject
    lateinit var actualInjector: DispatchingAndroidInjector<UpdaterApp>

    override fun androidInjector(): AndroidInjector<Any> = actualInjector as AndroidInjector<Any>

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

        // Note: Injection of default rawPreference must be prior than dependencies
        // injection! Because the modules like network depends on the default
        // preference to instantiate.
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

    @Inject
    lateinit var schedulers: IThreadSchedulers
    @Inject
    lateinit var appPreference: IAppPreference

    private fun injectDependencies() {
        DaggerAppComponent.builder()
            .setApplication(this)
            .setAppPreference(rawPreference)
            .setContentResolver(contentResolver)
            .build()
            .inject(this)
    }

    // Updater Engine /////////////////////////////////////////////////////////

    private val rawPreference by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private fun generateUpdaterConfig(): ApkUpdaterConfig {
        val hostPackageName = packageName
        val heartbeatInterval = rawPreference.getInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS).toMilliseconds()
        val checkInterval = rawPreference.getInt(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS).toMilliseconds()
        val downloadUseCache = rawPreference.getBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
        val installHourBegin = rawPreference.getInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
        val installHourEnd = rawPreference.getInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
        val installAllowDowngrade = rawPreference.getBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)

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

    /**
     * Inject the necessary default configuration to the application preference.
     * Note: Don't use injected instance in this method cause the app will crash
     * since the DI isn't setup yet.
     */
    private fun injectDefaultPreferences() {
        // Debug device ID
        try {
            if (Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID) == null &&
                (BuildUtils.isDebug() || BuildUtils.isStaging())) {
                Timber.v("[Updater] Inject the debug device ID as \"$DEBUG_DEVICE_ID\"")
                Settings.Secure.putString(contentResolver, SharedSettingsProps.DEVICE_ID, DEBUG_DEVICE_ID)
            }
            val deviceID = Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID)
            Timber.v("[Updater] The device ID is \"$deviceID\"")
        } catch (error: Throwable) {
            Timber.w("[Updater] Unable to access device ID due to no WRITE_SECURE_SETTINGS!\n$error")
        }

        // Network
        if (!rawPreference.contains(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS)) {
            rawPreference.edit()
                .putInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS)) {
            rawPreference.edit()
                .putInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS)) {
            rawPreference.edit()
                .putInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS)
                .apply()
        }

        // API General
        if (!rawPreference.contains(PreferenceProps.API_BASE_URL)) {
            val urls = BuildConfig.BASE_URLS
            val defaultURL = urls.last()
            Timber.v("[Updater] Inject the default API base URL, \"$defaultURL\"")
            rawPreference.edit()
                .putString(PreferenceProps.API_BASE_URL, defaultURL)
                .apply()
        }

        // Heartbeat
        if (!rawPreference.contains(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS)) {
            rawPreference.edit()
                .putInt(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
                .apply()
        }

        // Check
        if (!rawPreference.contains(PreferenceProps.CHECK_INTERVAL_SECONDS)) {
            rawPreference.edit()
                .putInt(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)
                .apply()
        }

        // Download
        if (!rawPreference.contains(PreferenceProps.DOWNLOAD_USE_CACHE)) {
            rawPreference.edit()
                .putBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
                .apply()
        }

        // Install
        if (!rawPreference.contains(PreferenceProps.INSTALL_HOUR_BEGIN)) {
            rawPreference.edit()
                .putInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.INSTALL_HOUR_END)) {
            rawPreference.edit()
                .putInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.INSTALL_ALLOW_DOWNGRADE)) {
            rawPreference.edit()
                .putBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)
                .apply()
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun observeSystemConfigChange() {
        // Restart the process for all kinds of rawPreference change!
        appPreference.observeAnyChange()
            .debounce(Intervals.DEBOUNCE_VALUE_CHANGE, TimeUnit.MILLISECONDS)
            .observeOn(schedulers.main())
            .subscribe({
                Timber.v("[Updater] System configuration changes, so restart the process!")
                // Forcefully flush
                appPreference.forceFlush()
                // Then restart the process
                ProcessPhoenix.triggerRebirth(this@UpdaterApp)
            }, Timber::e)
            .addTo(globalDisposables)
    }
}