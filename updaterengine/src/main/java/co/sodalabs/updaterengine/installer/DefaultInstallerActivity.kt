package co.sodalabs.updaterengine.installer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import co.sodalabs.updaterengine.data.Apk
import timber.log.Timber

private const val REQUEST_CODE_INSTALL = 0
private const val REQUEST_CODE_UNINSTALL = 1

/**
A transparent activity as a wrapper around Android's PackageInstaller Intents
 */
class DefaultInstallerActivity : FragmentActivity() {

    companion object {
        const val ACTION_INSTALL_PACKAGE = "co.sodalabs.apkupdater.installer.DefaultInstaller.action.INSTALL_PACKAGE"
        const val ACTION_UNINSTALL_PACKAGE = "co.sodalabs.apkupdater.installer.DefaultInstaller.action.UNINSTALL_PACKAGE"
    }

    private var downloadUri: Uri? = null

    private val apk: Apk by lazy { intent.getParcelableExtra(Installer.EXTRA_APK) as Apk }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            DefaultInstallerActivity.ACTION_INSTALL_PACKAGE -> {
                val localApkUri = intent.data
                downloadUri = intent.getParcelableExtra(Installer.EXTRA_DOWNLOAD_URI)
                installPackage(localApkUri)
            }
            DefaultInstallerActivity.ACTION_UNINSTALL_PACKAGE -> uninstallPackage(apk.packageName)
            else -> throw IllegalStateException("Intent action not specified!")
        }
    }

    @SuppressLint("InlinedApi")
    private fun installPackage(uri: Uri?) {
        if (uri == null) {
            throw RuntimeException("Set the data uri to point to an apk location!")
        }

        // https://code.google.com/p/android/issues/detail?id=205827
        if (Build.VERSION.SDK_INT < 24 && uri.scheme != "file") {
            throw RuntimeException("PackageInstaller < Android N only supports file scheme!")
        }
        if (Build.VERSION.SDK_INT >= 24 && uri.scheme != "content") {
            throw RuntimeException("PackageInstaller >= Android N only supports content scheme!")
        }

        val intent = Intent()

        // Note regarding EXTRA_NOT_UNKNOWN_SOURCE:
        // works only when being installed as system-app
        // https://code.google.com/p/android/issues/detail?id=42253
        if (Build.VERSION.SDK_INT < 24) {
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = uri
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        } else { // Android N
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = uri
            // grant READ permission for this content Uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_INSTALL)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
            val downloadUri = downloadUri ?: throw IllegalStateException("Download URI is null on install request")
            // installer.sendBroadcastInstall(downloadUri, Installer.ACTION_INSTALL_INTERRUPTED, "This Android rom does not support ACTION_INSTALL_PACKAGE!")
            finish()
        }
    }

    private fun uninstallPackage(packageName: String) {
        // check that the package is installed
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_INTERRUPTED, "Package that is scheduled for uninstall is not installed!")
            finish()
            return
        }

        val uri = Uri.fromParts("package", packageName, null)
        val intent = Intent()
        intent.data = uri
        intent.action = Intent.ACTION_UNINSTALL_PACKAGE
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)

        try {
            startActivityForResult(intent, REQUEST_CODE_UNINSTALL)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
            // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_INTERRUPTED, "This Android rom does not support ACTION_UNINSTALL_PACKAGE!")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_INSTALL -> {
                val downloadUri = downloadUri ?: throw IllegalStateException("Download URI is null on install request")
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.i("Install complete: $downloadUri")
                        // installer.sendBroadcastInstall(downloadUri, Installer.ACTION_INSTALL_COMPLETE)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.i("Install cancelled: $downloadUri")
                        // installer.sendBroadcastInstall(downloadUri, Installer.ACTION_INSTALL_INTERRUPTED)
                    }
                    Activity.RESULT_FIRST_USER ->
                        // AOSP returns Activity.RESULT_FIRST_USER on error
                        // installer.sendBroadcastInstall(downloadUri, Installer.ACTION_INSTALL_INTERRUPTED, getString(R.string.install_error_unknown))
                        Timber.i("Install error: $downloadUri")
                    else -> {
                        Timber.i("Install error: $downloadUri")
                        // installer.sendBroadcastInstall(downloadUri, Installer.ACTION_INSTALL_INTERRUPTED, getString(R.string.install_error_unknown))
                    }
                }
            }
            REQUEST_CODE_UNINSTALL -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.i("Uninstall complete: $downloadUri")
                        // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_COMPLETE)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.i("Uninstall cancelled: $downloadUri")
                        // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_INTERRUPTED)
                    }
                    Activity.RESULT_FIRST_USER -> {
                        // AOSP UninstallAppProgress returns RESULT_FIRST_USER on error
                        // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_INTERRUPTED, getString(R.string.uninstall_error_unknown))
                        Timber.i("Uninstall error: $downloadUri")
                    }
                    else -> {
                        // installer.sendBroadcastUninstall(Installer.ACTION_UNINSTALL_INTERRUPTED, getString(R.string.uninstall_error_unknown))
                        Timber.i("Uninstall error: $downloadUri")
                    }
                }
            }
            else -> throw RuntimeException("Invalid request code!")
        }

        // after doing the broadcasts, finish this transparent wrapper activity
        finish()
    }
}