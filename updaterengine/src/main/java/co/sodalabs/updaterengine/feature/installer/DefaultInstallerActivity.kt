package co.sodalabs.updaterengine.feature.installer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.BuildConfig
import timber.log.Timber

private const val REQUEST_CODE_INSTALL = 0
private const val REQUEST_CODE_UNINSTALL = 1

/**
 * A transparent activity as a wrapper around Android's PackageInstaller Intents
 */
class DefaultInstallerActivity : FragmentActivity() {

    companion object {
        const val ACTION_INSTALL_PACKAGE = "${BuildConfig.APPLICATION_ID}.install_package"
        const val ACTION_UNINSTALL_PACKAGE = "${BuildConfig.APPLICATION_ID}.uninstall_package"

        const val PROP_FILE_URI = "${BuildConfig.APPLICATION_ID}.file_uri"
        const val PROP_PACKAGE_NAME = "${BuildConfig.APPLICATION_ID}.package_name"
        const val PROP_RESULT_BOOLEAN = "${BuildConfig.APPLICATION_ID}.result_boolean"
    }

    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.v("[Install] The transparent default-install Activity is online")

        when (intent.action) {
            ACTION_INSTALL_PACKAGE -> {
                val fileURI = intent.getParcelableExtra<Uri>(PROP_FILE_URI)
                val packageName = intent.getStringExtra(PROP_PACKAGE_NAME)
                launchSystemUIForInstall(fileURI, packageName)
            }
            ACTION_UNINSTALL_PACKAGE -> {
                val packageName = intent.getStringExtra(PROP_PACKAGE_NAME)
                uninstallPackage(packageName)
            }
            else -> throw IllegalStateException("Intent action not specified!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.v("[Install] The transparent default-install Activity is offline")
    }

    @SuppressLint("InlinedApi")
    private fun launchSystemUIForInstall(
        fileURI: Uri,
        packageName: String
    ) {
        // https://code.google.com/p/android/issues/detail?id=205827
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N && fileURI.scheme != "file") {
            throw RuntimeException("PackageInstaller < Android N only supports file scheme!")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && fileURI.scheme != "content") {
            throw RuntimeException("PackageInstaller >= Android N only supports content scheme!")
        }

        val intent = Intent()

        // Note regarding EXTRA_NOT_UNKNOWN_SOURCE:
        // works only when being installed as system-app
        // https://code.google.com/p/android/issues/detail?id=42253
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Timber.v("[Install] Prepare the intent with manner (< 24)")
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = fileURI
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
        } else { // Android N
            Timber.v("[Install] Prepare the intent with manner (>= 24)")
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = fileURI
            // grant READ permission for this content Uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
        }

        try {
            Timber.v("[Install] Delegate to system UI for installing \"$packageName\" (from \"$fileURI\")")
            startActivityForResult(intent, REQUEST_CODE_INSTALL)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
            finish()
        }
    }

    private fun uninstallPackage(
        packageName: String
    ) {
        // check that the package is installed
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e)
            finish()
            return
        }

        val uri = Uri.fromParts("package", packageName, null)
        val intent = Intent()
        intent.data = uri
        intent.action = Intent.ACTION_UNINSTALL_PACKAGE
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)

        try {
            Timber.v("[Install] Delegate to system UI for uninstalling \"$packageName\"")
            startActivityForResult(intent, REQUEST_CODE_UNINSTALL)
        } catch (e: ActivityNotFoundException) {
            Timber.e(e)
            notifyViaLocalBroadcast(ACTION_INSTALL_PACKAGE, false)
            finish()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        val packageName = intent.getStringExtra(PROP_PACKAGE_NAME)
        when (requestCode) {
            REQUEST_CODE_INSTALL -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.v("[Install] Install completes for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_INSTALL_PACKAGE, true)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.v("[Install] Install gets cancelled for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_INSTALL_PACKAGE, false)
                    }
                    else -> {
                        Timber.e("[Install] Install fails for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_INSTALL_PACKAGE, false)
                    }
                }
            }
            REQUEST_CODE_UNINSTALL -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.v("[Install] Uninstall completes for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_UNINSTALL_PACKAGE, true)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.v("[Install] Uninstall gets cancelled for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_UNINSTALL_PACKAGE, false)
                    }
                    else -> {
                        Timber.e("[Install] Uninstall fails for \"$packageName\"")
                        notifyViaLocalBroadcast(ACTION_UNINSTALL_PACKAGE, false)
                    }
                }
            }
            else -> throw RuntimeException("Invalid request code!")
        }

        // after doing the broadcasts, finish this transparent wrapper activity
        finish()
    }

    private fun notifyViaLocalBroadcast(
        action: String,
        isOk: Boolean
    ) {
        val intent = Intent(action)
        intent.putExtra(PROP_RESULT_BOOLEAN, isOk)
        broadcastManager.sendBroadcast(intent)
    }
}