package co.sodalabs.apkupdater.feature.adminui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.UpdaterApp
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

private val REQUEST_CODE_PERMISSIONS = 123

class SettingsActivity : AppCompatActivity() {

    private val disposes = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.v("App Updater UI is online")
        injectDependencies()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        logSSLProtocols()
        requestPermissions()

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

    private fun requestPermissions() {
        val allPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(
                Manifest.permission.REQUEST_INSTALL_PACKAGES)
            else -> arrayOf()
        }
        val missingPermissions = mutableListOf<String>()
        for (i in 0 until allPermissions.size) {
            val permission = allPermissions[i]
            if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, permission)) {
                missingPermissions.add(permission)
            }
        }
        if (missingPermissions.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            } else {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
            }
        }
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