package co.sodalabs.apkupdater.feature.adminui

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.extension.ALWAYS_RETRY
import co.sodalabs.updaterengine.extension.smartRetryWhen
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber

private const val CODE_PROCESSING = 0

private const val KEY_HEART_BEAT_NOW = "report_heartbeat_now"

class SettingsFragment : PreferenceFragmentCompat() {

    private val disposables = CompositeDisposable()
    private val preferenceClicks = PublishRelay.create<Preference>()

    private val sendHeartBeatPref by lazy {
        findPreference<Preference>(KEY_HEART_BEAT_NOW) ?: throw IllegalStateException("Can't find heart beat preference!")
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onResume() {
        super.onResume()
        observeHeartBeatNowClicks()
    }

    override fun onPause() {
        disposables.clear()
        super.onPause()
    }

    override fun onPreferenceTreeClick(
        preference: Preference
    ): Boolean {
        return when (preference) {
            sendHeartBeatPref -> {
                preferenceClicks.accept(preference)
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    private fun observeHeartBeatNowClicks() {
        val cacheTitle: String = sendHeartBeatPref.title.toString()

        preferenceClicks
            .filter { it == sendHeartBeatPref }
            .flatMap {
                ApkUpdater.sendHeartBeatNow()
                    .toObservable()
                    .startWith(CODE_PROCESSING)
            }
            .smartRetryWhen(ALWAYS_RETRY, 1000L, AndroidSchedulers.mainThread()) { true }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ code ->
                when (code) {
                    CODE_PROCESSING -> {
                        sendHeartBeatPref.isEnabled = false
                        // Use title to present working state
                        sendHeartBeatPref.title = "$cacheTitle (Working...)"
                    }
                    else -> {
                        sendHeartBeatPref.isEnabled = true
                        sendHeartBeatPref.title = cacheTitle

                        context?.let { c ->
                            Toast.makeText(c, "Heart beat returns $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }, Timber::e)
            .addTo(disposables)
    }
}