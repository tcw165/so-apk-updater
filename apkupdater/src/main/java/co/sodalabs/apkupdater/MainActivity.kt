package co.sodalabs.apkupdater

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import co.sodalabs.apkupdater.utils.ConfigHelper
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.data.Apk
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val disposeBag = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ApkUpdater.addCallback(object : ApkUpdater.OnUpdateAvailableCallback {
            override fun onUpdateAvailable(apk: Apk, updateMessage: String) {
                val message = "Update Available:" +
                    "\n${apk.packageName} v${apk.versionName}" +
                    "\n\n$updateMessage"
                Timber.i("onUpdateAvailable: $apk - $updateMessage")
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }

            override fun onUpdateDownloadFailed(apk: Apk, reason: String) {
                Timber.i("onUpdateDownloadFailed: $apk - $reason")
                Toast.makeText(this@MainActivity, "Update Download Failed:\n$reason", Toast.LENGTH_SHORT).show()
            }

            override fun onUpdateDownloaded(apk: Apk) {
                Timber.i("onUpdateDownloaded: $apk")
                Toast.makeText(
                    this@MainActivity,
                    "Update Downloaded:\n${apk.packageName} v${apk.versionName}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        swAutoDownload.setOnCheckedChangeListener { _, isChecked ->
            val config = ConfigHelper.getDefault(this)
            config.setAutoDownload(isChecked)
            ApkUpdater.updateConfig(config)
        }

        swAutoInstall.setOnCheckedChangeListener { _, isChecked ->
            val config = ConfigHelper.getDefault(this)
            config.setAutoInstall(isChecked)
            ApkUpdater.updateConfig(config)
        }

        btCheckForUpdates.setOnClickListener {
            ApkUpdater.checkForUpdatesNow()
        }
    }

    override fun onDestroy() {
        disposeBag.dispose()
        super.onDestroy()
    }
}
