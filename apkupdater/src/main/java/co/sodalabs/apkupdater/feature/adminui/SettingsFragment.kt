package co.sodalabs.apkupdater.feature.adminui

import Packages
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.data.UiState
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import co.sodalabs.updaterengine.data.HTTPResponseCode
import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.smartRetryWhen
import co.sodalabs.updaterengine.feature.logPersistence.LogsPersistenceLauncher
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.support.AndroidSupportInjection
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import javax.inject.Inject

private const val KEY_API_BASE_URL = PreferenceProps.API_BASE_URL
private const val KEY_API_UPDATE_CHANNEL = PreferenceProps.API_UPDATE_CHANNEL
private const val KEY_VERSIONS = "versions"
private const val KEY_HEART_BEAT_WATCHER = "heartbeat_watcher"
private const val KEY_HEART_BEAT_NOW = "send_heartbeat_now"
private const val KEY_CHECK_UPDATE_NOW = "check_test_app_now"
private const val KEY_CHECK_STATUS = "check_status"
private const val KEY_SHOW_ANDROID_SETTINGS = "androidSettings"
private const val KEY_HOME_INTENT = "home_intent"
private const val KEY_SHOW_INTERNET_SPEED_TEST = "speedTestApp"
private const val KEY_SEND_LOGS = "sendLogs"

private const val PACKAGE_APK_UPDATER = BuildConfig.APPLICATION_ID
private const val PACKAGE_PRIVILEGED_INSTALLER = co.sodalabs.updaterengine.Packages.PRIVILEGED_EXTENSION_PACKAGE_NAME
private const val PACKAGE_SPARKPOINT = Packages.SPARKPOINT_PACKAGE_NAME

class SettingsFragment :
    PreferenceFragmentCompat(),
    ISettingsScreen {

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var heartbeater: UpdaterHeartBeater
    @Inject
    lateinit var schedulers: IThreadSchedulers
    @Inject
    lateinit var packageVersionProvider: IPackageVersionProvider
    @Inject
    lateinit var appPreference: IAppPreference
    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var systemProperties: ISystemProperties
    @Inject
    lateinit var logsPersistenceLauncher: LogsPersistenceLauncher
    @Inject
    lateinit var timeUtil: ITimeUtil

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidSupportInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()

        setupBaseURLPreference()
        setupUpdateChannelPreference()
        observeVersions()
        observeHeartBeatNowClicks()
        observeRecurringHeartBeat()

        observeCheckUpdateNowClicks()
        observeOtherButtonClicks()

        observeCaughtErrors()
    }

    override fun onPause() {
        disposables.clear()
        super.onPause()
    }

    // Versions ///////////////////////////////////////////////////////////////

    private val versionsPref by lazy {
        findPreference<Preference>(KEY_VERSIONS)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private fun observeVersions() {
        observeVersionsRelatedInformation()
            .startWith(Unit)
            .switchMapSingle { getVersionsSingle() }
            .observeOn(schedulers.main())
            .subscribe({ versionsString ->
                versionsPref.summary = versionsString
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeVersionsRelatedInformation(): Observable<Unit> {
        // Upstream is the combination of initial trigger and the later change
        // trigger.
        return Observable
            .merge<Unit>(
                sharedSettings.observeDeviceId().map { Unit },
                appPreference.observeAnyChange().map { Unit }
            )
    }

    private fun getVersionsSingle(): Single<String> {
        return Single
            .fromCallable {
                val sb = StringBuilder()

                val deviceID = try {
                    sharedSettings.getDeviceId()
                } catch (securityError: SecurityException) {
                    "don't have permission"
                }
                sb.appendln("Device ID: '$deviceID'")
                sb.appendln("Hardware ID: '${sharedSettings.getHardwareId()}'")
                sb.appendln("Firmware Version: '${systemProperties.getFirmwareVersion()}'")

                sb.appendln("Updater Version: '${packageVersionProvider.getPackageVersion(PACKAGE_APK_UPDATER)}'")
                sb.appendln("Updater Build Hash: '${BuildConfig.GIT_SHA}'")
                sb.appendln("Updater Build Type: '${BuildConfig.BUILD_TYPE}'")

                sb.appendln("Privileged Installer Version: '${packageVersionProvider.getPackageVersion(PACKAGE_PRIVILEGED_INSTALLER)}'")
                sb.appendln("Sparkpoint Player Version: '${packageVersionProvider.getPackageVersion(PACKAGE_SPARKPOINT)}'")
                sb.toString()
            }
            .subscribeOn(schedulers.io())
    }

    // API ////////////////////////////////////////////////////////////////////

    private val apiBaseURLPref by lazy {
        findPreference<ListPreference>(KEY_API_BASE_URL)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val apiUpdateChannelPref by lazy {
        findPreference<ListPreference>(KEY_API_UPDATE_CHANNEL)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private fun setupBaseURLPreference() {
        apiBaseURLPref.entries = BuildConfig.BASE_URLS
        apiBaseURLPref.entryValues = BuildConfig.BASE_URLS
    }

    private fun setupUpdateChannelPreference() {
        apiUpdateChannelPref.entries = BuildConfig.UPDATE_CHANNELS
        apiUpdateChannelPref.entryValues = BuildConfig.UPDATE_CHANNELS
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private val heartBeatWatcherPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_WATCHER)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val heartbeatWatcherTitle by lazy { heartBeatWatcherPref.title.toString() }

    private val sendHeartBeatNowPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_NOW)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val sendHeartbeatNowTitle by lazy { sendHeartBeatNowPref.title.toString() }

    @Suppress("USELESS_CAST")
    private fun observeHeartBeatNowClicks() {
        val safeContext = context ?: throw NullPointerException("Context is null")

        sendHeartBeatNowPref.clicks()
            .observeOn(schedulers.main())
            .flatMap {
                heartbeater.sendHeartBeatNow()

                val intentFilter = IntentFilter(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
                RxLocalBroadcastReceiver.bind(safeContext, intentFilter)
                    .map { intent ->
                        val statusCode = intent.getIntExtra(IntentActions.PROP_HTTP_RESPONSE_CODE, 0)
                        UiState.Done(statusCode) as UiState<Int>
                    }
                    .startWith(UiState.InProgress())
                    .take(2)
                    .subscribeOn(schedulers.main())
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                if (error is DeviceNotSetupException) {
                    false
                } else {
                    caughtErrorRelay.accept(error)
                    true
                }
            }
            .observeOn(schedulers.main())
            .subscribe({ uiState ->
                when (uiState) {
                    is UiState.InProgress<Int> -> {
                        markHeartBeatWIP()
                    }
                    is UiState.Done<Int> -> {
                        markHeartBeatDone()

                        val httpCode = uiState.data
                        context?.let { c ->
                            val message = when (httpCode) {
                                HTTPResponseCode.NotFound.code -> "Heartbeat returns $httpCode. Are you sure this device exists on Airtable?"
                                HTTPResponseCode.UnprocessableEntity.code -> "Heartbeat returns $httpCode. There is something wrong with your request."
                                HTTPResponseCode.OK.code -> "Heartbeat returns $httpCode. Heartbeat was a success."
                                else -> "Heart beat returns $httpCode. An unknown error occured."
                            }

                            Toast.makeText(c, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeRecurringHeartBeat() {
        var last = "???"
        heartbeater.observeRecurringHeartBeat()
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                if (error is DeviceNotSetupException) {
                    false
                } else {
                    markHeartBeatDone()
                    caughtErrorRelay.accept(error)
                    true
                }
            }
            .observeOn(schedulers.main())
            .subscribe({ code ->
                val now = timeUtil.systemZonedNow().toString()
                heartBeatWatcherPref.title = "$heartbeatWatcherTitle at $last (HTTP status code: $code)"
                last = now
            }, Timber::e)
            .addTo(disposables)
    }

    private fun markHeartBeatWIP() {
        sendHeartBeatNowPref.isEnabled = false
        // Use title to present working state
        sendHeartBeatNowPref.title = "$sendHeartbeatNowTitle (Working...)"
    }

    private fun markHeartBeatDone() {
        sendHeartBeatNowPref.isEnabled = true
        sendHeartBeatNowPref.title = sendHeartbeatNowTitle
    }

    // Check Update ///////////////////////////////////////////////////////////

    private val checkUpdateNowPref by lazy {
        findPreference<Preference>(KEY_CHECK_UPDATE_NOW)
            ?: throw java.lang.IllegalStateException("Can't find preference!")
    }
    private val checkUpdateNowTitle by lazy { checkUpdateNowPref.title.toString() }

    private val checkStatusPref by lazy {
        findPreference<Preference>(KEY_CHECK_STATUS)
            ?: throw java.lang.IllegalStateException("Can't find preference!")
    }

    @Suppress("USELESS_CAST")
    private fun observeCheckUpdateNowClicks() {
        val safeContext = context ?: throw NullPointerException("Context is null")

        checkUpdateNowPref.clicks()
            .observeOn(schedulers.main())
            .flatMap {
                UpdaterService.checkUpdateNow(
                    safeContext,
                    resetSession = true,
                    installImmediately = true
                )

                // TODO: Pull out to a function of ApkUpdater.
                val intentFilter = IntentFilter(IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE)
                RxLocalBroadcastReceiver.bind(safeContext, intentFilter)
                    .map {
                        UiState.Done(true) as UiState<Boolean>
                    }
                    .startWith(UiState.InProgress())
                    .take(2)
                    .subscribeOn(schedulers.main())
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, schedulers.computation()) { error ->
                markCheckUpdateDone()
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(schedulers.main())
            .subscribe({ uiState ->
                when (uiState) {
                    is UiState.InProgress<Boolean> -> {
                        markCheckUpdateWIP()
                    }
                    is UiState.Done<Boolean> -> {
                        markCheckUpdateDone()
                    }
                }
            }, Timber::e)
            .addTo(disposables)

        val intentFilter = IntentFilter().apply {
            addAction(IntentActions.ACTION_CHECK_UPDATE)
            addAction(IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE)
            addAction(IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE)
            addAction(IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_ERROR)

            addAction(IntentActions.ACTION_DOWNLOAD_APP_UPDATE)
            addAction(IntentActions.ACTION_DOWNLOAD_APP_UPDATE_PROGRESS)
            addAction(IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE)
            addAction(IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE)
            addAction(IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS)
            addAction(IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE)

            addAction(IntentActions.ACTION_INSTALL_APP_UPDATE)
            addAction(IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE)
            addAction(IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE)
            addAction(IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE)
            addAction(IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_ERROR)
        }
        RxLocalBroadcastReceiver.bind(safeContext, intentFilter)
            .observeOn(schedulers.main())
            .subscribe({ intent ->
                when (intent.action) {
                    IntentActions.ACTION_CHECK_UPDATE -> checkStatusPref.summary = "Check"
                    IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE -> {
                        val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
                        val message = if (updates.isEmpty()) {
                            "Check... No updates found"
                        } else {
                            "Check... found available app updates"
                        }
                        checkStatusPref.summary = message
                    }
                    IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE -> {
                        val foundUpdate = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        checkStatusPref.summary = "Check... found available firmware update, '${foundUpdate.fileURL}'"
                    }
                    IntentActions.ACTION_CHECK_FIRMWARE_UPDATE_ERROR -> {
                        val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable
                        checkStatusPref.summary = "Check... failed for firmware, $error"
                    }

                    // Download section
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE -> {
                        checkStatusPref.summary = "Download... apps"
                    }
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE_PROGRESS -> {
                        val update = intent.getParcelableExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        val progressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, 0)
                        checkStatusPref.summary = "Download... '${update.packageName}' at $progressPercentage%"
                    }
                    IntentActions.ACTION_DOWNLOAD_APP_UPDATE_COMPLETE -> {
                        val update = intent.getParcelableExtra<AppUpdate?>(IntentActions.PROP_FOUND_UPDATE)
                        update?.let {
                            checkStatusPref.summary = "Download... '${it.packageName}' finished"
                        }
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE -> {
                        checkStatusPref.summary = "Download... firmware"
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS -> {
                        val update = intent.getParcelableExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATE)
                        val progressPercentage = intent.getIntExtra(IntentActions.PROP_PROGRESS_PERCENTAGE, 0)
                        checkStatusPref.summary = "Download... '${update.fileURL}' at $progressPercentage%"
                    }
                    IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE -> {
                        val update = intent.getParcelableExtra<FirmwareUpdate?>(IntentActions.PROP_FOUND_UPDATE)
                        update?.let {
                            checkStatusPref.summary = "Download... '${it.fileURL}' finished"
                        }
                    }

                    // Install section
                    IntentActions.ACTION_INSTALL_APP_UPDATE -> {
                        checkStatusPref.summary = "Install... downloaded apps"
                    }
                    IntentActions.ACTION_INSTALL_APP_UPDATE_COMPLETE -> {
                        checkStatusPref.summary = "Idle"
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE -> {
                        checkStatusPref.summary = "Install... downloaded firmware"
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE -> {
                        checkStatusPref.summary = "Idle"
                    }
                    IntentActions.ACTION_INSTALL_FIRMWARE_UPDATE_ERROR -> {
                        checkStatusPref.summary = "Install... failed for firmware"
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    private fun markCheckUpdateWIP() {
        checkUpdateNowPref.isEnabled = false
        // Use title to present working state
        checkUpdateNowPref.title = "$checkUpdateNowTitle (Working...)"
    }

    private fun markCheckUpdateDone() {
        checkUpdateNowPref.isEnabled = true
        checkUpdateNowPref.title = checkUpdateNowTitle
    }

    // Error //////////////////////////////////////////////////////////////////

    private val caughtErrorRelay = PublishRelay.create<Throwable>().toSerialized()

    private fun observeCaughtErrors() {
        caughtErrorRelay
            .observeOn(schedulers.main())
            .subscribe({ error ->
                context?.let { c ->
                    Toast.makeText(c, "Capture $error", Toast.LENGTH_LONG).show()
                }
            }, Timber::e)
            .addTo(disposables)
    }

    // RxPreference ///////////////////////////////////////////////////////////

    private val preferenceClicks = PublishRelay.create<Preference>().toSerialized()

    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean {
        preferenceClicks.accept(preference)
        return true
    }

    private fun Preference.clicks(): Observable<Unit> {
        val thisPreference = this
        return preferenceClicks
            .observeOn(schedulers.main())
            .filter { thisPreference.isEnabled && it == thisPreference }
            .map { Unit }
    }

    // Other buttons //////////////////////////////////////////////////////////

    private val showAndroidSettingsPref by lazy {
        findPreference<Preference>(KEY_SHOW_ANDROID_SETTINGS)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val homeIntentPref by lazy {
        findPreference<Preference>(KEY_HOME_INTENT)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val showInternetSpeedTestPref by lazy {
        findPreference<Preference>(KEY_SHOW_INTERNET_SPEED_TEST)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val sendLogsPref by lazy {
        findPreference<Preference>(KEY_SEND_LOGS)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private fun observeOtherButtonClicks() {
        showAndroidSettingsPref.clicks()
            .observeOn(schedulers.main())
            .subscribe({
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }, Timber::e)
            .addTo(disposables)

        homeIntentPref.clicks()
            .observeOn(schedulers.main())
            .subscribe({
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
            }, Timber::e)
            .addTo(disposables)

        showInternetSpeedTestPref.clicks()
            .observeOn(schedulers.main())
            .subscribe({
                val safeContext = context ?: return@subscribe
                val pm = safeContext.packageManager
                val speedTestAppIntent = pm.getLaunchIntentForPackage(Packages.NET_SPEED_TEST_PACKAGE_NAME)

                speedTestAppIntent?.let { intent ->
                    try {
                        startActivity(intent.apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    } catch (error: Throwable) {
                        Timber.e(error)
                    }
                } ?: kotlin.run {
                    Toast.makeText(safeContext, "Cannot find the launch Intent for '${Packages.NET_SPEED_TEST_PACKAGE_NAME}'", Toast.LENGTH_LONG).show()
                }
            }, Timber::e)
            .addTo(disposables)

        sendLogsPref.clicks()
            .flatMap { logsPersistenceLauncher.backupLogToCloudNow() }
            .observeOn(schedulers.main())
            .subscribe({ success ->
                val message = if (success) {
                    R.string.send_logs_success_msg
                } else {
                    R.string.send_logs_failure_msg
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }, Timber::e)
            .addTo(disposables)
    }
}