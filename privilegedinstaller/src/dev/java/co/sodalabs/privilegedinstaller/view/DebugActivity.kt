package co.sodalabs.privilegedinstaller.view

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.privilegedinstaller.BuildConfig
import co.sodalabs.privilegedinstaller.IPrivilegedService
import co.sodalabs.privilegedinstaller.PrivilegedService
import co.sodalabs.privilegedinstaller.R
import co.sodalabs.privilegedinstaller.RxServiceConnection
import co.sodalabs.privilegedinstaller.exceptions.RxServiceConnectionError
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber

class DebugActivity : AppCompatActivity() {

    private val disposablesOnResumePause = CompositeDisposable()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_debug)
    }

    override fun onResume() {
        super.onResume()

        Timber.v("DEBUG Activity binds ")
        bindInstallerService()
    }

    override fun onPause() {
        super.onPause()

        disposablesOnResumePause.clear()
    }

    // Service Binding ////////////////////////////////////////////////////////

    private var service: IPrivilegedService? = null

    private fun bindInstallerService() {
        val serviceClassName = PrivilegedService::class.java.canonicalName ?: throw RxServiceConnectionError("Unable to find the privileged service name")
        val serviceIntent = Intent()
        serviceIntent.setClassName(BuildConfig.APPLICATION_ID, serviceClassName)

        RxServiceConnection.bind(this@DebugActivity, serviceIntent)
            .map { IPrivilegedService.Stub.asInterface(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ service ->
                this.service = service
                val granted = service.hasPrivilegedPermissions()
                Timber.v("The DEBUG Activity is granted: $granted")
            }, Timber::e)
            .addTo(disposablesOnResumePause)
    }
}