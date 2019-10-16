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
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.HTTPResponseCode
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.getPrettyDateNow
import co.sodalabs.updaterengine.extension.smartRetryWhen
import com.jakewharton.rxrelay2.PublishRelay
import dagger.android.support.AndroidSupportInjection
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import javax.inject.Inject

private const val KEY_API_BASE_URL = PreferenceProps.API_BASE_URL
private const val KEY_HEART_BEAT_WATCHER = "heartbeat_watcher"
private const val KEY_HEART_BEAT_NOW = "send_heartbeat_now"
private const val KEY_CHECK_UPDATE_NOW = "check_test_app_now"
private const val KEY_SHOW_ANDROID_SETTINGS = "androidSettings"
private const val KEY_SHOW_INTERNET_SPEED_TEST = "speedTestApp"

class SettingsFragment :
    PreferenceFragmentCompat(),
    ISettingsScreen {

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var heartbeater: UpdaterHeartBeater
    @Inject
    lateinit var appPreference: IAppPreference

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

    // API ////////////////////////////////////////////////////////////////////

    private val apiBaseURLPref by lazy {
        findPreference<ListPreference>(KEY_API_BASE_URL)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private fun setupBaseURLPreference() {
        apiBaseURLPref.entries = BuildConfig.BASE_URLS
        apiBaseURLPref.entryValues = BuildConfig.BASE_URLS
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
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
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
            .observeOn(AndroidSchedulers.mainThread())
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                markHeartBeatDone()
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ code ->
                val now = getPrettyDateNow()
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

    @Suppress("USELESS_CAST")
    private fun observeCheckUpdateNowClicks() {
        val safeContext = context ?: throw NullPointerException("Context is null")

        checkUpdateNowPref.clicks()
            .flatMap {
                UpdaterService.checkUpdateNow(safeContext, resetSession = true)

                // TODO: Pull out to a function of ApkUpdater.
                val intentFilter = IntentFilter(IntentActions.ACTION_CHECK_APP_UPDATE_COMPLETE)
                RxLocalBroadcastReceiver.bind(safeContext, intentFilter)
                    .map {
                        UiState.Done(true) as UiState<Boolean>
                    }
                    .startWith(UiState.InProgress())
                    .take(2)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                markCheckUpdateDone()
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
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
            .observeOn(AndroidSchedulers.mainThread())
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
            .filter { thisPreference.isEnabled && it == thisPreference }
            .map { Unit }
    }

    // Other buttons //////////////////////////////////////////////////////////

    private val showAndroidSettingsPref by lazy {
        findPreference<Preference>(KEY_SHOW_ANDROID_SETTINGS)
            ?: throw IllegalStateException("Can't find preference!")
    }
    private val showInternetSpeedTestPref by lazy {
        findPreference<Preference>(KEY_SHOW_INTERNET_SPEED_TEST)
            ?: throw IllegalStateException("Can't find preference!")
    }

    private fun observeOtherButtonClicks() {
        showAndroidSettingsPref.clicks()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }, Timber::e)
            .addTo(disposables)

        showInternetSpeedTestPref.clicks()
            .observeOn(AndroidSchedulers.mainThread())
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
    }
}