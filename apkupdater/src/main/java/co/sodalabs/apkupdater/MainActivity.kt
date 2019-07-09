package co.sodalabs.apkupdater

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.updaterengine.ApkUpdater
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.btCheckForUpdates
import timber.log.Timber
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class MainActivity : AppCompatActivity() {

    private val disposeBag = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.v("App Updater UI is online")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //        ApkUpdater.setCallback(object : ApkUpdaterCallback {
        //            override fun onUpdateAvailable(apk: Apk, updateMessage: String): Boolean {
        //                val message = "Update Available:" +
        //                    "\n${apk.fromUpdate.packageName} v${apk.fromUpdate.versionName}" +
        //                    "\n\n$updateMessage"
        //                Timber.i("onUpdateAvailable: $apk - $updateMessage")
        //                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        //
        //                return swAutoDownload.isChecked
        //            }
        //
        //            override fun onUpdateDownloadFailed(apk: Apk, reason: String) {
        //                Timber.i("onUpdateDownloadFailed: $apk - $reason")
        //                Toast.makeText(this@MainActivity, "Update Download Failed:\n$reason", Toast.LENGTH_SHORT).show()
        //            }
        //
        //            override fun onUpdateDownloaded(apk: Apk): Boolean {
        //                Timber.i("onUpdateDownloaded: $apk")
        //                Toast.makeText(
        //                    this@MainActivity,
        //                    "Update Downloaded:\n${apk.fromUpdate.packageName} v${apk.fromUpdate.versionName}",
        //                    Toast.LENGTH_SHORT
        //                ).show()
        //
        //                return swAutoInstall.isChecked
        //            }
        //        })

        btCheckForUpdates.setOnClickListener {
            ApkUpdater.checkForUpdatesNow()
        }

        showSSLProtocols()
    }

    override fun onDestroy() {
        Timber.v("[Updater] App Updater UI is offline")
        disposeBag.dispose()
        super.onDestroy()
    }

    private fun showSSLProtocols() {
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
