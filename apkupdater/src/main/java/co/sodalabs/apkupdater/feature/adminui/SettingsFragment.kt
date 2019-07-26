package co.sodalabs.apkupdater.feature.adminui

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.getPrettyDateNow
import co.sodalabs.updaterengine.extension.smartRetryWhen
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber

private const val CODE_PROCESSING = 0

private const val KEY_HEART_BEAT_WATCHER = "heartbeat_watcher"
private const val KEY_HEART_BEAT_NOW = "report_heartbeat_now"

class SettingsFragment : PreferenceFragmentCompat() {

    private val disposables = CompositeDisposable()
    private val preferenceClicks = PublishRelay.create<Preference>().toSerialized()
    private val caughtErrorRelay = PublishRelay.create<Throwable>().toSerialized()

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

        observeCaughtErrors()
    }

    override fun onPause() {
        disposables.clear()
        super.onPause()
    }

    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean {
        return when (preference) {
            sendHeartBeatNowPref -> {
                preferenceClicks.accept(preference)
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private val heartBeatWatcherPref by lazy { findPreference<Preference>(KEY_HEART_BEAT_WATCHER) ?: throw IllegalStateException("Can't find preference!") }
    private val heartbeatWatcherTitle by lazy { heartBeatWatcherPref.title.toString() }

    private val sendHeartBeatNowPref by lazy { findPreference<Preference>(KEY_HEART_BEAT_NOW) ?: throw IllegalStateException("Can't find preference!") }
    private val sendHeartbeatNowTitle by lazy { sendHeartBeatNowPref.title.toString() }

    private fun observeHeartBeatNowClicks() {
        preferenceClicks
            .filter { it == sendHeartBeatNowPref }
            .flatMap {
                ApkUpdater.sendHeartBeatNow()
                    .toObservable()
                    .startWith(CODE_PROCESSING)
            }
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
                caughtErrorRelay.accept(error)
                true
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ code ->
                when (code) {
                    CODE_PROCESSING -> {
                        markHeartBeatWIP()
                    }
                    else -> {
                        markHeartBeatDone()
                        context?.let { c ->
                            Toast.makeText(c, "Heart beat returns $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }

    private fun observeRecurringHeartBeat() {
        var last = "???"
        ApkUpdater.observeHeartBeat()
            .smartRetryWhen(ALWAYS_RETRY, Intervals.RETRY_AFTER_1S, AndroidSchedulers.mainThread()) { error ->
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

    // Error //////////////////////////////////////////////////////////////////

    private fun observeCaughtErrors() {
        caughtErrorRelay
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ error ->
                markHeartBeatDone()
                context?.let { c ->
                    Toast.makeText(c, "Heart beat throws $error", Toast.LENGTH_SHORT).show()
                }
            }, Timber::e)
            .addTo(disposables)
    }
}