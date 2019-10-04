package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.utils.BugsnagTree
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.SharedSettingsProps
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.UpdatesChecker
import co.sodalabs.updaterengine.UpdatesDownloader
import co.sodalabs.updaterengine.UpdatesInstaller
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

private const val DEBUG_DEVICE_ID = "999999"
private const val DEBUG_FIRMWARE_VERSION = "1.0.0"
private const val DEBUG_SPARKPOINT_VERSION = "0.2.6.1"

class UpdaterApp :
    MultiDexApplication(),
    HasAndroidInjector {

    @Inject
    lateinit var actualInjector: DispatchingAndroidInjector<UpdaterApp>

    override fun androidInjector(): AndroidInjector<Any> = actualInjector as AndroidInjector<Any>

    @Inject
    lateinit var updatesChecker: UpdatesChecker
    @Inject
    lateinit var updatesDownloader: UpdatesDownloader
    @Inject
    lateinit var updatesInstaller: UpdatesInstaller
    @Inject
    lateinit var heartBeater: UpdaterHeartBeater

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
        injectDefaultPreferencesBeforeInjectingDep()
        injectDependencies()
        logSystemInfo()

        observeSystemConfigChange()

        // Install the updater engine after everything else is ready.
        UpdaterService.start(this)
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

    /**
     * Note: Call this after the dependencies are injected.
     */
    private fun logSystemInfo() {
        val firmwareVersion = rawPreference.getString(PreferenceProps.MOCK_FIRMWARE_VERSION, null)
            ?: systemProperties.getString(SystemProps.FIRMWARE_VERSION_INCREMENTAL, "")
        Timber.v("[Updater] The firmware version is \"$firmwareVersion\"")
    }

    // Application Singletons /////////////////////////////////////////////////

    @Inject
    lateinit var schedulers: IThreadSchedulers
    @Inject
    lateinit var appPreference: IAppPreference
    @Inject
    lateinit var systemProperties: ISystemProperties

    private val rawPreference by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private fun injectDependencies() {
        DaggerAppComponent.builder()
            .setApplication(this)
            .setAppPreference(rawPreference)
            .setContentResolver(contentResolver)
            .setPackageManager(packageManager)
            .build()
            .inject(this)
    }

    // Preferences ////////////////////////////////////////////////////////////

    /**
     * Inject the necessary default configuration to the application preference.
     * Note: Don't use injected instance in this method cause the app will crash
     * since the DI isn't setup yet.
     */
    @SuppressLint("ApplySharedPref")
    private fun injectDefaultPreferencesBeforeInjectingDep() {
        // Debug device ID
        try {
            if (Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID) == null &&
                (BuildUtils.isDebug())) {
                Timber.v("[Updater] Inject the debug device ID as \"$DEBUG_DEVICE_ID\"")
                Settings.Secure.putString(contentResolver, SharedSettingsProps.DEVICE_ID, DEBUG_DEVICE_ID)
            }
            val deviceID = Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID)
            Timber.v("[Updater] The device ID is \"$deviceID\"")
        } catch (error: Throwable) {
            Timber.w("[Updater] Unable to access device ID due to no WRITE_SECURE_SETTINGS!\n$error")
        }

        // Mock firmware version
        if (!rawPreference.contains(PreferenceProps.MOCK_FIRMWARE_VERSION)) {
            if (BuildUtils.isDebug()) {
                rawPreference.edit()
                    .putString(PreferenceProps.MOCK_FIRMWARE_VERSION, DEBUG_FIRMWARE_VERSION)
                    .apply()
            }
        }
        if (!rawPreference.contains(PreferenceProps.MOCK_SPARKPOINT_VERSION)) {
            if (BuildUtils.isDebug()) {
                rawPreference.edit()
                    .putString(PreferenceProps.MOCK_SPARKPOINT_VERSION, DEBUG_SPARKPOINT_VERSION)
                    .apply()
            }
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
        val apiBaseURL = rawPreference.getString(PreferenceProps.API_BASE_URL, "")
        Timber.v("[Updater] API base URL, \"$apiBaseURL\"")

        // Heartbeat
        try {
            // Remove the incompatible property type for version new than 0.11.3.
            rawPreference.getLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, 0L)
        } catch (ignored: Throwable) {
            rawPreference.edit()
                .remove(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS)) {
            rawPreference.edit()
                .putLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
                .apply()
        }

        // Check
        try {
            // Remove the incompatible property type for version new than 0.11.3.
            rawPreference.getLong(PreferenceProps.CHECK_INTERVAL_SECONDS, 0L)
        } catch (ignored: Throwable) {
            rawPreference.edit()
                .remove(PreferenceProps.CHECK_INTERVAL_SECONDS)
                .apply()
        }
        if (!rawPreference.contains(PreferenceProps.CHECK_INTERVAL_SECONDS)) {
            rawPreference.edit()
                .putLong(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)
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