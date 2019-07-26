package co.sodalabs.apkupdater.feature.adminui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.UpdaterApp
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class SettingsActivity : AppCompatActivity() {

    private val disposes = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.v("App Updater UI is online")
        injectDependencies()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        logSSLProtocols()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onDestroy() {
        Timber.v("[Updater] App Updater UI is offline")
        disposes.dispose()
        super.onDestroy()
    }

    private fun injectDependencies() {
        val appComponent = UpdaterApp.appComponent
        appComponent.inject(this)
    }

    private fun logSSLProtocols() {
        try {
            Timber.v("SSL Protocols: [")
            val sslParameters: SSLParameters = SSLContext.getDefault().defaultSSLParameters
            val protocols = sslParameters.protocols
            for (i in 0 until protocols.size) {
                val protocol = protocols[i]
                Timber.v("    $protocol,")
            }
            Timber.v("]")
        } catch (err: Throwable) {
            Timber.e(err)
        }
    }
}