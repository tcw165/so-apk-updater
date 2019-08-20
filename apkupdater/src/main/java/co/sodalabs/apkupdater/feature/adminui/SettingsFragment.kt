package co.sodalabs.apkupdater.feature.adminui

import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.data.UiState
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.getPrettyDateNow
import co.sodalabs.updaterengine.extension.smartRetryWhen
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber

private const val KEY_HEART_BEAT_WATCHER = "heartbeat_watcher"
private const val KEY_HEART_BEAT_NOW = "send_heartbeat_now"
private const val KEY_CHECK_UPDATE_NOW = "check_test_app_now"
private const val KEY_DOWNLOAD_TEST_APP_NOW = "download_test_app_now"

class SettingsFragment : PreferenceFragmentCompat() {

    private val disposables = CompositeDisposable()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()

        observeHeartBeatNowClicks()
        observeRecurringHeartBeat()

        observeCheckUpdateNowClicks()
        observeDownloadTestNowClicks()

        observeCaughtErrors()
    }

    override fun onPause() {
        disposables.clear()
        super.onPause()
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private val heartBeatWatcherPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_WATCHER) ?: throw IllegalStateException("Can't find preference!")
    }
    private val heartbeatWatcherTitle by lazy { heartBeatWatcherPref.title.toString() }

    private val sendHeartBeatNowPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_NOW) ?: throw IllegalStateException("Can't find preference!")
    }
    private val sendHeartbeatNowTitle by lazy { sendHeartBeatNowPref.title.toString() }

    @Suppress("USELESS_CAST")
    private fun observeHeartBeatNowClicks() {
        val safeContext = context ?: throw NullPointerException("Context is null")

        sendHeartBeatNowPref.clicks()
            .flatMap {
                ApkUpdater.sendHeartBeatNow()

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
                            Toast.makeText(c, "Heart beat returns $httpCode", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeRecurringHeartBeat() {
        var last = "???"
        ApkUpdater.observeHeartBeat()
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

    // Check Update

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
                ApkUpdater.checkForUpdatesNow()

                val intentFilter = IntentFilter(IntentActions.ACTION_CHECK_UPDATES)
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

    // Update/Download ////////////////////////////////////////////////////////

    private val downloadTestAppNowPref by lazy {
        findPreference<Preference>(KEY_DOWNLOAD_TEST_APP_NOW) ?: throw IllegalStateException("Can't find preference!")
    }
    private val downloadTestAppNowTitle by lazy { downloadTestAppNowPref.title.toString() }

    @Suppress("USELESS_CAST")
    private fun observeDownloadTestNowClicks() {
        val safeContext = context ?: throw NullPointerException("Context is null")

        downloadTestAppNowPref.clicks()
            .flatMap {
                ApkUpdater.downloadUpdateNow(FakeUpdates.file170MB)

                val intentFilter = IntentFilter(IntentActions.ACTION_DOWNLOAD_UPDATES)
                RxLocalBroadcastReceiver.bind(safeContext, intentFilter)
                    .map { intent ->
                        val downloadedUpdates =
                            intent.getParcelableArrayListExtra<DownloadedUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
                        UiState.Done(downloadedUpdates.toList()) as UiState<List<DownloadedUpdate>>
                    }
                    .startWith(UiState.InProgress())
                    .take(2)
            }
            .observeOn(AndroidSchedulers.mainThread())
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                markTestDownloadDone()
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ uiState ->
                when (uiState) {
                    is UiState.InProgress<List<DownloadedUpdate>> -> {
                        markTestDownloadWIP()
                    }
                    is UiState.Done<List<DownloadedUpdate>> -> {
                        // val apks = uiState.data
                        // Timber.v("[Download] files: $apks")
                        markTestDownloadDone()
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    private fun markTestDownloadWIP() {
        downloadTestAppNowPref.isEnabled = false
        // Use title to present working state
        downloadTestAppNowPref.title = "$downloadTestAppNowTitle (Working...)"
    }

    private fun markTestDownloadDone() {
        downloadTestAppNowPref.isEnabled = true
        downloadTestAppNowPref.title = downloadTestAppNowTitle
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
}