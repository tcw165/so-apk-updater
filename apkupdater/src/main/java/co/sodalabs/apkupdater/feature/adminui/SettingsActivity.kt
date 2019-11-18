package co.sodalabs.apkupdater.feature.adminui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import co.sodalabs.apkupdater.IAutoExitHelper
import co.sodalabs.apkupdater.IPasscodeDialogFactory
import co.sodalabs.apkupdater.ITouchTracker
import co.sodalabs.apkupdater.R
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.Intervals
import com.jakewharton.rxbinding3.view.clicks
import dagger.android.AndroidInjection
import dagger.android.AndroidInjector
import dagger.android.DispatchingAndroidInjector
import dagger.android.HasAndroidInjector
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import kotlinx.android.synthetic.main.activity_settings.btBack
import timber.log.Timber
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

private val REQUEST_CODE_PERMISSIONS = 123

class SettingsActivity :
    AppCompatActivity(),
    HasAndroidInjector {

    @Inject
    lateinit var passcodeDialogFactory: IPasscodeDialogFactory
    @Inject
    lateinit var touchTracker: ITouchTracker
    @Inject
    lateinit var autoExitHelper: IAutoExitHelper
    @Inject
    lateinit var schedulers: IThreadSchedulers
    @Inject
    lateinit var actualInjector: DispatchingAndroidInjector<Any>

    override fun androidInjector(): AndroidInjector<Any> = actualInjector

    private val disposesOnCreateDestroy = CompositeDisposable()
    private val disposesOnResumePause = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        Timber.v("App Updater UI is online")
        logSSLProtocols()
        requestPermissions()

        // TODO: Pull out to Presenter
        showPasscodeDialog()
        observeCloseClicks()

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    override fun onDestroy() {
        Timber.v("[Updater] App Updater UI is offline")
        disposesOnCreateDestroy.dispose()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        startAutoExitCountdown()
    }

    override fun onPause() {
        disposesOnResumePause.clear()
        super.onPause()
    }

    override fun dispatchTouchEvent(
        ev: MotionEvent
    ): Boolean {
        touchTracker.trackEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun startAutoExitCountdown() {
        if (BuildUtils.isDebug()) return

        autoExitHelper.startAutoExitCountDown(Intervals.AUTO_EXIT)
            .subscribe({}, Timber::e)
            .addTo(disposesOnResumePause)
    }

    private fun showPasscodeDialog() {
        passcodeDialogFactory.showPasscodeDialog()
            .subscribe({ authorized ->
                if (!authorized) {
                    finish()
                }
            }, Timber::e)
            .addTo(disposesOnCreateDestroy)
    }

    private fun observeCloseClicks() {
        btBack.clicks()
            .observeOn(schedulers.main())
            .subscribe({
                finish()
            }, Timber::e)
            .addTo(disposesOnCreateDestroy)
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