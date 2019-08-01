package co.sodalabs.apkupdater.feature.adminui

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.data.UiState
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
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
private const val KEY_HEART_BEAT_NOW = "report_heartbeat_now"
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

        observeDownloadTestNowClicks()

        observeCaughtErrors()
    }

    override fun onPause() {
        disposables.clear()
        super.onPause()
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private val heartBeatWatcherPref by lazy { findPreference<Preference>(KEY_HEART_BEAT_WATCHER) ?: throw IllegalStateException("Can't find preference!") }
    private val heartbeatWatcherTitle by lazy { heartBeatWatcherPref.title.toString() }

    private val sendHeartBeatNowPref by lazy { findPreference<Preference>(KEY_HEART_BEAT_NOW) ?: throw IllegalStateException("Can't find preference!") }
    private val sendHeartbeatNowTitle by lazy { sendHeartBeatNowPref.title.toString() }

    @Suppress("USELESS_CAST")
    private fun observeHeartBeatNowClicks() {
        sendHeartBeatNowPref.clicks()
            .flatMap {
                ApkUpdater.sendHeartBeatNow()
                    .map { UiState.Done(it) as UiState<Int> }
                    .toObservable()
                    .startWith(UiState.InProgress())
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

    // Update/Download ////////////////////////////////////////////////////////

    private val downloadTestAppNowPref by lazy { findPreference<Preference>(KEY_DOWNLOAD_TEST_APP_NOW) ?: throw IllegalStateException("Can't find preference!") }
    private val downloadTestAppNowTitle by lazy { downloadTestAppNowPref.title.toString() }

    private val fakeUpdate = AppUpdate(
        packageName = "co.sodalabs.sparkpoint",
        downloadUrl = "https://sparkdatav0.blob.core.windows.net/apks/Sparkpoint-debug-0.2.5.apk",
        hash = "doesn't really matter",
        versionCode = 0,
        versionName = "0.0.0")

    @Suppress("USELESS_CAST")
    private fun observeDownloadTestNowClicks() {
        downloadTestAppNowPref.clicks()
            .flatMap {
                ApkUpdater.downloadUpdateNow(fakeUpdate)
                    .map { UiState.Done(it) as UiState<Apk> }
                    .toObservable()
                    .startWith(UiState.InProgress())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                markTestDownloadDone("???")
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ uiState ->
                when (uiState) {
                    is UiState.InProgress<Apk> -> {
                        markTestDownloadWIP()
                    }
                    is UiState.Done<Apk> -> {
                        val apk = uiState.data
                        val apkFilePath = apk.file.canonicalPath
                        markTestDownloadDone(apkFilePath)
                        Toast.makeText(activity, "The test update is downloaded under \"$apkFilePath\" directory.", Toast.LENGTH_LONG).show()
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

    private fun markTestDownloadDone(
        filePath: String
    ) {
        downloadTestAppNowPref.isEnabled = true
        downloadTestAppNowPref.title = "$downloadTestAppNowTitle (file is stored here, \"$filePath\")"
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