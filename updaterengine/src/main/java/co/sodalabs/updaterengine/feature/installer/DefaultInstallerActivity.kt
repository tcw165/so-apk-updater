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
import co.sodalabs.updaterengine.IntentActions
import timber.log.Timber

private const val REQUEST_CODE_INSTALL = 0
private const val REQUEST_CODE_UNINSTALL = 1

/**
 * A transparent activity as a wrapper around Android's PackageInstaller Intents
 */
class DefaultInstallerActivity : FragmentActivity() {

    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.v("[Install] The transparent default-install Activity is online")

        val packageName = intent.getStringExtra(IntentActions.PROP_APP_PACKAGE_NAME)
        when (intent.action) {
            IntentActions.ACTION_INSTALL_APP -> {
                val localApkUri = intent.getParcelableExtra<Uri>(IntentActions.PROP_APP_FILE_URI)
                launchSystemUIForInstall(localApkUri, packageName)
            }
            IntentActions.ACTION_UNINSTALL_APP -> uninstallPackage(packageName)
            else -> throw IllegalStateException("Intent action not specified!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.v("[Install] The transparent default-install Activity is offline")
    }

    @SuppressLint("InlinedApi")
    private fun launchSystemUIForInstall(
        localApkUri: Uri,
        packageName: String
    ) {
        // https://code.google.com/p/android/issues/detail?id=205827
        if (Build.VERSION.SDK_INT < 24 && localApkUri.scheme != "file") {
            throw RuntimeException("PackageInstaller < Android N only supports file scheme!")
        }
        if (Build.VERSION.SDK_INT >= 24 && localApkUri.scheme != "content") {
            throw RuntimeException("PackageInstaller >= Android N only supports content scheme!")
        }

        val intent = Intent()

        // Note regarding EXTRA_NOT_UNKNOWN_SOURCE:
        // works only when being installed as system-app
        // https://code.google.com/p/android/issues/detail?id=42253
        if (Build.VERSION.SDK_INT < 24) {
            Timber.v("[Install] Prepare the intent with manner (< 24)")
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = localApkUri
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
            // Updater engine data
            intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAME, packageName)
            intent.putExtra(IntentActions.PROP_APP_FILE_URI, localApkUri)
        } else { // Android N
            Timber.v("[Install] Prepare the intent with manner (>= 24)")
            intent.action = Intent.ACTION_INSTALL_PACKAGE
            intent.data = localApkUri
            // grant READ permission for this content Uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, false)
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, applicationInfo.packageName)
            // Updater engine data
            intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAME, packageName)
            intent.putExtra(IntentActions.PROP_APP_FILE_URI, localApkUri)
        }

        try {
            Timber.v("[Install] Delegate to system UI for installing \"$packageName\" (from \"$localApkUri\")")
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
            finish()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        // val localApkUri = safeIntent.getParcelableExtra<Uri>(PROP_APP_FILE_URI)
        val packageName = intent.getStringExtra(IntentActions.PROP_APP_PACKAGE_NAME)

        when (requestCode) {
            REQUEST_CODE_INSTALL -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.v("[Install] Install completes for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_INSTALL_SUCCESSFULLY, packageName)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.v("[Install] Install gets cancelled for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_INSTALL_CANCELLED, packageName)
                    }
                    Activity.RESULT_FIRST_USER -> {
                        // AOSP returns Activity.RESULT_FIRST_USER on error
                        Timber.e("[Install] Install fails for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_INSTALL_FAILED, packageName)
                    }
                    else -> {
                        Timber.e("[Install] Install fails for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_INSTALL_FAILED, packageName)
                    }
                }
            }
            REQUEST_CODE_UNINSTALL -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        Timber.v("[Install] Uninstall completes for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_UNINSTALL_SUCCESSFULLY, packageName)
                    }
                    Activity.RESULT_CANCELED -> {
                        Timber.v("[Install] Uninstall gets cancelled for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_UNINSTALL_CANCELLED, packageName)
                    }
                    Activity.RESULT_FIRST_USER -> {
                        Timber.e("[Install] Uninstall fails for \"$packageName\"")
                        // AOSP UninstallAppProgress returns RESULT_FIRST_USER on error
                        notifyViaLocalBroadcast(IntentActions.ACTION_UNINSTALL_FAILED, packageName)
                    }
                    else -> {
                        Timber.e("[Install] Uninstall fails for \"$packageName\"")
                        notifyViaLocalBroadcast(IntentActions.ACTION_UNINSTALL_FAILED, packageName)
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
        packageName: String
    ) {
        val intent = Intent()
        intent.action = action
        intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAME, packageName)

        broadcastManager.sendBroadcast(intent)
    }
}