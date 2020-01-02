package co.sodalabs.apkupdater.feature.adminui

import Packages
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.data.UiState
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.HTTPResponseCode
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.support.AndroidSupportInjection
import io.reactivex.Observable
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

class SettingsFragment :
    PreferenceFragmentCompat(),
    ISettingsScreen {

    @Inject
    lateinit var presenter: SettingsPresenter

    private val preferenceClicks = PublishRelay.create<Preference>().toSerialized()

    // UI Streams /////////////////////////////////////

    override val sendHeartBeatNowPrefClicks by lazy { sendHeartBeatNowPref.clicks() }

    override val checkUpdateNowPrefClicks by lazy { checkUpdateNowPref.clicks() }

    override val showAndroidSettingsPrefClicks by lazy { showAndroidSettingsPref.clicks() }

    override val homeIntentPrefClicks by lazy { homeIntentPref.clicks() }

    override val internetSpeedTestPrefClicks by lazy { showInternetSpeedTestPref.clicks() }

    override val sendLogsPrefClicks by lazy { sendLogsPref.clicks() }

    // Preferences

    private val versionsPref by lazy {
        findPreference<Preference>(KEY_VERSIONS)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val apiBaseURLPref by lazy {
        findPreference<ListPreference>(KEY_API_BASE_URL)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val apiUpdateChannelPref by lazy {
        findPreference<ListPreference>(KEY_API_UPDATE_CHANNEL)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val heartBeatWatcherPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_WATCHER)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val sendHeartBeatNowPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_NOW)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private val sendHeartbeatNowTitle by lazy { sendHeartBeatNowPref.title.toString() }

    private val checkUpdateNowPref by lazy {
        findPreference<Preference>(KEY_CHECK_UPDATE_NOW)
            ?: throw java.lang.IllegalStateException("Can't find preference!")
    }

    private val checkUpdateNowTitle by lazy { checkUpdateNowPref.title.toString() }

    private val checkStatusPref by lazy {
        findPreference<Preference>(KEY_CHECK_STATUS)
            ?: throw java.lang.IllegalStateException("Can't find preference!")
    }

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
        presenter.resume()
    }

    override fun onPause() {
        presenter.pause()
        super.onPause()
    }

    // RxPreference ///////////////////////////////////////////////////////////

    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean {
        preferenceClicks.accept(preference)
        return true
    }

    private fun Preference.clicks(): Observable<Unit> {
        val thisPreference = this
        return preferenceClicks
            .filter { thisPreference.isEnabled && it == thisPreference }
            .map { Unit }
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    override fun sendHeartBeatNowBroadcast(): Observable<UiState<Int>> {
        val intentFilter = IntentFilter(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
        return RxLocalBroadcastReceiver.bind(requireContext(), intentFilter)
            .map { intent ->
                val statusCode = intent.getIntExtra(IntentActions.PROP_HTTP_RESPONSE_CODE, 0)
                UiState.Done(statusCode) as UiState<Int>
            }
            .startWith(UiState.InProgress())
            .take(2)
    }

    override fun sendHeartBeatCompleteBroadcast(): Observable<UiState<Boolean>> {
        UpdaterService.checkUpdateNow(
            requireContext(),
            resetSession = true,
            installImmediately = true
        )

        // TODO: Pull out to a function of ApkUpdater.
        val intentFilter = IntentFilter(IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE)
        return RxLocalBroadcastReceiver.bind(requireContext(), intentFilter)
            .map {
                UiState.Done(true) as UiState<Boolean>
            }
            .startWith(UiState.InProgress())
            .take(2)
    }

    override fun updateHeartBeatNowPrefState(state: UiState<Int>) {
        when (state) {
            is UiState.InProgress<Int> -> {
                markHeartBeatWIP()
            }
            is UiState.Done<Int> -> {
                markHeartBeatDone()

                val message = when (val httpCode = state.data) {
                    HTTPResponseCode.NotFound.code -> "Heartbeat returns $httpCode. Are you sure this device exists on Airtable?"
                    HTTPResponseCode.UnprocessableEntity.code -> "Heartbeat returns $httpCode. There is something wrong with your request."
                    HTTPResponseCode.OK.code -> "Heartbeat returns $httpCode. Heartbeat was a success."
                    else -> "Heart beat returns $httpCode. An unknown error occured."
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun updateHeartBeatCheckPrefState(state: UiState<Boolean>) {
        when (state) {
            is UiState.InProgress<Boolean> -> markCheckUpdateWIP()
            is UiState.Done<Boolean> -> markCheckUpdateDone()
        }
    }

    override fun markHeartBeatDone() {
        sendHeartBeatNowPref.apply {
            isEnabled = true
            title = sendHeartbeatNowTitle
        }
    }

    override fun markHeartBeatWIP() {
        sendHeartBeatNowPref.apply {
            isEnabled = false
            // Use title to present working state
            title = "$sendHeartbeatNowTitle (Working...)"
        }
    }

    override fun markCheckUpdateDone() {
        checkUpdateNowPref.apply {
            isEnabled = true
            title = checkUpdateNowTitle
        }
    }

    override fun markCheckUpdateWIP() {
        checkUpdateNowPref.apply {
            isEnabled = false
            // Use title to present working state
            title = "$checkUpdateNowTitle (Working...)"
        }
    }

    override fun observeUpdaterBroadcasts(): Observable<Intent> {
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
        return RxLocalBroadcastReceiver.bind(requireContext(), intentFilter)
    }

    override fun updateUpdateStatusPrefSummary(message: String) {
        checkStatusPref.summary = message
    }

    // API ////////////////////////////////////////////////////////////////////

    override fun setupBaseURLPreference(urls: Array<String>) {
        apiBaseURLPref.entries = urls
        apiBaseURLPref.entryValues = urls
    }

    override fun setupUpdateChannelPreference(channels: Array<String>) {
        apiUpdateChannelPref.entries = channels
        apiUpdateChannelPref.entryValues = channels
    }

    // Version ////////////////////////////////////////////////////////////////

    override fun updateVersionPrefSummary(versionsString: String?) {
        versionsPref.summary = versionsString
    }

    override fun updateHeartBeatPrefTitle(verbalResult: String?) {
        heartBeatWatcherPref.title = verbalResult
    }

    // Others /////////////////////////////////////////////////////////////////

    override fun showErrorMessage(error: Throwable) {
        Toast.makeText(requireContext(), "Capture $error", Toast.LENGTH_LONG).show()
    }

    override fun goToAndroidSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun goToSpeedTest() {
        val pm = requireContext().packageManager
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
            Toast.makeText(
                requireContext(),
                "Cannot find the launch Intent for '${Packages.NET_SPEED_TEST_PACKAGE_NAME}'",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun showLogSendSuccessMessage(success: Boolean) {
        val message = if (success) {
            R.string.send_logs_success_msg
        } else {
            R.string.send_logs_failure_msg
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}