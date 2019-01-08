package co.sodalabs.updaterengine.installer

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_UNINSTALL_PACKAGE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import co.sodalabs.updaterengine.data.Apk

/**
The default installer of F-Droid. It uses the normal Intents APIs of Android
to install apks. Its main inner workings are encapsulated in DefaultInstallerActivity.

This is installer requires user interaction and thus install/uninstall directly
return PendingIntents.
 */
class DefaultInstaller(context: Context, apk: Apk) : Installer(context, apk) {

    override fun installPackageInternal(localApkUri: Uri, downloadUri: Uri) {

        val installIntent = Intent(context, DefaultInstallerActivity::class.java)
        installIntent.action = DefaultInstallerActivity.ACTION_INSTALL_PACKAGE
        installIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        installIntent.putExtra(EXTRA_DOWNLOAD_URI, downloadUri)
        installIntent.putExtra(EXTRA_APK, apk)
        installIntent.data = localApkUri

        context.applicationContext.startActivity(installIntent)
    }

    override fun uninstallPackage() {
        val uninstallIntent = Intent(context, DefaultInstallerActivity::class.java)
        uninstallIntent.action = ACTION_UNINSTALL_PACKAGE
        uninstallIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        uninstallIntent.putExtra(EXTRA_APK, apk)

        context.applicationContext.startActivity(uninstallIntent)
    }

    override fun isUnattended(): Boolean = false
}