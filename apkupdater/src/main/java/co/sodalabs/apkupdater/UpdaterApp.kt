package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.provider.Settings
import androidx.multidex.MultiDexApplication
import androidx.preference.PreferenceManager
import androidx.work.ListenableWorker
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.utils.BugsnagTree
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.SessionLoggingTree
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.SharedSettingsProps
import co.sodalabs.updaterengine.SharedSettingsProps.SERVER_ENVIRONMENT
import co.sodalabs.updaterengine.SharedSettingsProps.SPARKPOINT_REST_API_BASE_URL
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.di.HasWorkerInjector
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import com.jakewharton.processphoenix.ProcessPhoenix
import com.jakewharton.threetenabp.AndroidThreeTen
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.rxkotlin.addTo
import leakcanary.AppWatcher
import leakcanary.LeakCanary
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val DEBUG_DEVICE_ID = "999999"
private const val EMPTY_STRING = ""

class UpdaterApp :
    MultiDexApplication(),
    HasAndroidInjector,
    HasWorkerInjector {

    @Inject
    lateinit var actualAndroidInjector: DispatchingAndroidInjector<UpdaterApp>
    @Inject
    lateinit var actualWorkerInjector: DispatchingAndroidInjector<ListenableWorker>

    @Suppress("UNCHECKED_CAST")
    override fun androidInjector(): AndroidInjector<Any> = actualAndroidInjector as AndroidInjector<Any>

    override fun workerInjector(): AndroidInjector<ListenableWorker> = actualWorkerInjector

    @Inject
    lateinit var schedulers: IThreadSchedulers
    @Inject
    lateinit var appPreference: IAppPreference
    @Inject
    lateinit var systemProperties: ISystemProperties
    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var updaterStateTracker: IUpdaterStateTracker

    private val rawPreference by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val globalDisposables = CompositeDisposable()

    // This tree lives until we are done with dependency injection
    // where we replace this tree with a [co.sodalabs.apkupdater.utils.SessionLoggingTree]
    // tree that includes the session id
    private val treeBeforeInjection = Timber.DebugTree()

    /**
     * A most early checkpoint for initializing the Timber log.
     *
     * Note: This is called prior to Application's [onCreate] and ContentProvider's
     * onCreate.
     */
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        initLogging()
    }

    override fun onCreate() {
        super.onCreate()

        // Note: Injection of default rawPreference must be prior than dependencies
        // injection! Because the modules like network depends on the default
        // preference to instantiate.
        injectDefaultPreferencesBeforeInjectingDep()
        injectDependencies()
        initSessionLogging()
        initToggleableLogging()
        initNetworkEnvironment()
        initCrashReporting()
        initDatetime()
        initLeakCanary()
        initStrictMode()
        logSystemInfo()

        safeguardsUndeliverableException()

        restartAppIfPreferenceChanges()

        // Initialize the launching works.
        sendBroadcast(Intent(WorkOnAppLaunchInitializer.UPDATER_LAUNCH))

        // FIXME: Start the UpdaterService without the ACTION_ENGINE_START.
        //  Or handle the second engine start Intent?
        // A last safeguard to make sure the UpdaterService starts!
        val intent = Intent(this, UpdaterService::class.java).apply {
            action = IntentActions.ACTION_ENGINE_START
        }
        startService(intent)
    }

    private fun initLeakCanary() {
        // Note: LeakCanary initializes in ContentProvider.
        // Setting retained visibility to 0 to always dump the leak report.
        LeakCanary.config = LeakCanary.config.copy(
            retainedVisibleThreshold = 0
        )

        if (BuildUtils.isRelease()) {
            // We don't want this guy running in the background on DEBUG build.
            AppWatcher.config = AppWatcher.config.copy(enabled = false)
        }
    }

    private fun initStrictMode() {
        if (BuildUtils.isDebug() || BuildUtils.isPreRelease()) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                // Note: You'll see a lot of warning by enabling disk reads cause
                // the shared preference and system settings all cache the database
                // in MAIN thread in their initialization.
                // Basically, all the system components related to ContentProvider
                // share this common issue.
                // .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                // Note: The log uses LogCat directly and we have no control such as
                // logging to the crashlytics service.
                .penaltyLog()
                .penaltyFlashScreen()
                .build())
            StrictMode.setVmPolicy(VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build())
        }
    }

    @SuppressLint("LogNotTimber")
    private fun initLogging() {
        Timber.plant(treeBeforeInjection)
    }

    @SuppressLint("LogNotTimber")
    private fun initSessionLogging() {
        Timber.uproot(treeBeforeInjection)
        Timber.plant(SessionLoggingTree(appPreference))
        Timber.i("[Updater App] Replaced Debug Tree with Session Logging Tree")
    }

    @SuppressLint("LogNotTimber")
    private fun initToggleableLogging() {
        /* Do not remove, will need this again.
        val logTree = Timber.DebugTree()
        if (BuildUtils.isRelease()) {
            // Note: There would be a short latency on planting the log tree on
            // release build.
            sharedSettings.observeSecureBoolean(SharedSettingsProps.ADMIN_FORCEFULLY_LOGGABLE, false)
                .observeOn(schedulers.main())
                .subscribe({ forcefullyLoggable ->
                    val treeSnapshot = Timber.forest()
                    if (forcefullyLoggable) {
                        Log.w("[Updater]", "Enable the log :D")
                        if (!treeSnapshot.contains(logTree)) {
                            Timber.plant(logTree)
                        }
                    } else {
                        Log.w("[Updater]", "Disable the log...")
                        if (treeSnapshot.contains(logTree)) {
                            Timber.uproot(logTree)
                        }
                    }
                }, { err ->
                    Log.e("[Updater]", err.toString())
                })
                .addTo(globalDisposables)
        } else {
            // Otherwise, always log.
            Timber.plant(logTree)
        }
        */
    }

    private fun initCrashReporting() {
        val deviceID = sharedSettings.getSecureString(SharedSettingsProps.DEVICE_ID) ?: "device ID not set yet!"
        val config = Configuration(BuildConfig.BUGSNAG_API_KEY).apply {
            // Only send report for staging and release
            notifyReleaseStages = arrayOf(BuildUtils.TYPE_PRE_RELEASE, BuildUtils.TYPE_RELEASE)
            releaseStage = BuildConfig.BUILD_TYPE

            metaData.addToTab(MetadataProps.BUCKET_DEVICE, MetadataProps.KEY_DEVICE_ID, deviceID)
            metaData.addToTab(MetadataProps.TAB_BUILD_INFO, MetadataProps.KEY_DEVICE_ID, deviceID)
            metaData.addToTab(MetadataProps.TAB_BUILD_INFO, MetadataProps.KEY_HARDWARE_ID, sharedSettings.getHardwareId())
            metaData.addToTab(MetadataProps.TAB_BUILD_INFO, MetadataProps.KEY_FIRMWARE_VERSION, systemProperties.getFirmwareVersion())
        }

        val bugTracker = Bugsnag.init(this, config)
        bugTracker.setUserId(deviceID)
        bugTracker.beforeNotify {
            val (state, stateMetadata) = updaterStateTracker.snapshotStateWithMetadata()

            // Add the state machine state just after an error is captured
            it.addToTab(MetadataProps.TAB_UPDATER_STATE, MetadataProps.KEY_STATE, state.name)

            // Add all the little bits of metadata as well
            stateMetadata.entries
                .forEach { entry ->
                    val (key, value) = entry
                    it.addToTab(MetadataProps.TAB_UPDATER_STATE, key, value)
                }

            true
        }

        Timber.plant(BugsnagTree(bugTracker))
    }

    private fun initDatetime() {
        AndroidThreeTen.init(this)
    }

    private fun safeguardsUndeliverableException() {
        // The global error funnel for RxJava
        RxJavaPlugins.setErrorHandler { error ->
            // Observable/Single/Maybe/Completable/Flowable's 'fromCallable'
            // sends errors to here if the stream is disposed but some error
            // needs to be taken care of.
            Timber.e(error)
        }
    }

    /**
     * Note: Call this after the dependencies are injected.
     */
    private fun logSystemInfo() {
        val firmwareVersion = rawPreference.getString(PreferenceProps.MOCK_FIRMWARE_VERSION, null)
            ?: systemProperties.getString(SystemProps.FIRMWARE_VERSION_INCREMENTAL, "")
        Timber.v("[Updater] App version name: ${BuildConfig.VERSION_NAME}")
        Timber.v("[Updater] App version code: ${BuildConfig.VERSION_CODE}")
        Timber.v("[Updater] Firmware version: \"$firmwareVersion\"")
    }

    // Application Singletons /////////////////////////////////////////////////

    @SuppressLint("LogNotTimber")
    private fun injectDependencies() {
        // Using LogCat instead Timber here cause the injection for Timber is NOT
        // ready yet!
        Timber.v("[Updater] Initializing dependencies...")

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
    @Deprecated("Remove all these injection when we retire settings.xml")
    private fun injectDefaultPreferencesBeforeInjectingDep() {
        // Debug device ID
        try {
            if (Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID) == null &&
                (BuildUtils.isDebug())
            ) {
                Timber.v("[Updater] Inject the debug device ID as \"$DEBUG_DEVICE_ID\"")
                Settings.Secure.putString(contentResolver, SharedSettingsProps.DEVICE_ID, DEBUG_DEVICE_ID)
            }
            val deviceID = Settings.Secure.getString(contentResolver, SharedSettingsProps.DEVICE_ID)
            Timber.v("[Updater] The device ID is \"$deviceID\"")
        } catch (error: Throwable) {
            Timber.w("[Updater] Unable to access device ID due to no WRITE_SECURE_SETTINGS!\n$error")
        }

        // Admin Passcode
        if (Settings.Secure.getString(contentResolver, SharedSettingsProps.ADMIN_PASSCODE) == null) {
            Timber.v("[Updater] The admin passcode is '${BuildConfig.ADMIN_PASSCODE}'")
            Settings.Secure.putString(contentResolver, SharedSettingsProps.ADMIN_PASSCODE, BuildConfig.ADMIN_PASSCODE)
        } else {
            val passcode = Settings.Secure.getString(contentResolver, SharedSettingsProps.ADMIN_PASSCODE)
                ?: throw IllegalStateException()
            Timber.v("[Updater] The admin passcode is '$passcode'")
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
        if (!rawPreference.contains(PreferenceProps.INSTALL_ALLOW_APP_DOWNGRADE)) {
            rawPreference.edit()
                .putBoolean(PreferenceProps.INSTALL_ALLOW_APP_DOWNGRADE, BuildConfig.INSTALL_ALLOW_APP_DOWNGRADE)
                .apply()
        }
    }

    private fun initNetworkEnvironment() {
        val defaultBaseUrl = BuildConfig.BASE_URLS.last()
        val apiBaseURL = rawPreference.getString(PreferenceProps.API_BASE_URL, defaultBaseUrl) ?: defaultBaseUrl

        sharedSettings.putGlobalString(SPARKPOINT_REST_API_BASE_URL, apiBaseURL)
        Timber.v("[Updater] Set Default API base URL, \"$apiBaseURL\"")

        val environment = ServerEnvironment.fromRawUrl(apiBaseURL)
        sharedSettings.putGlobalString(SERVER_ENVIRONMENT, environment.name)
        Timber.v("[Updater] Set Default API environment, \"$environment\"")
    }

    @SuppressLint("ApplySharedPref")
    private fun restartAppIfPreferenceChanges() {
        // FIXME: Please don't restart the process!
        // Restart the process for all kinds of rawPreference change!
        appPreference.observeAnyChange()
            .filter(this::ignoredProperties)
            .debounce(Intervals.DEBOUNCE_VALUE_CHANGE, TimeUnit.MILLISECONDS)
            .observeOn(schedulers.main())
            .subscribe({ key ->
                if (key == PreferenceProps.API_BASE_URL) {
                    val rawApiUrl = rawPreference.getString(PreferenceProps.API_BASE_URL, EMPTY_STRING) ?: EMPTY_STRING
                    val environment = ServerEnvironment.fromRawUrl(rawApiUrl)
                    sharedSettings.putGlobalString(SERVER_ENVIRONMENT, environment.name)
                    sharedSettings.putGlobalString(SPARKPOINT_REST_API_BASE_URL, rawApiUrl)
                }

                Timber.v("[Updater] System configuration changes, so restart the process!")
                // Forcefully flush
                appPreference.forceFlush()
                // Then restart the process
                ProcessPhoenix.triggerRebirth(this@UpdaterApp)
            }, Timber::e)
            .addTo(globalDisposables)
    }

    // TODO: There should be more sophisticated mechanism to ignore properties
    //  that we don't want to use for triggering restart engine event
    private fun ignoredProperties(key: String): Boolean {
        return key !in listOf(
            PreferenceProps.HEARTBEAT_VERBAL_RESULT,
            PreferenceProps.LOG_FILE_CREATED_TIMESTAMP,
            PreferenceProps.LAST_KNOWN_FIRMWARE_VERSION,
            PreferenceProps.INSTALL_PENDING_INSTALLS_TYPE,
            PreferenceProps.INSTALL_PENDING_INSTALLS_JSON,
            PreferenceProps.SESSION_ID
        )
    }
}