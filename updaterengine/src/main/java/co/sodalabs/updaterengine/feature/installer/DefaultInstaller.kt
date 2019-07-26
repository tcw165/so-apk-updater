package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_UNINSTALL_PACKAGE
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import co.sodalabs.updaterengine.IntentActions.ACTION_INSTALL_APP
import co.sodalabs.updaterengine.IntentActions.PROP_APP_FILE_URI
import co.sodalabs.updaterengine.IntentActions.PROP_APP_PACKAGE_NAME
import io.reactivex.Observable
import timber.log.Timber

/**
The default installer of F-Droid. It uses the normal Intents APIs of Android
to install apks. Its main inner workings are encapsulated in DefaultInstallerActivity.

This is installer requires user interaction and thus install/uninstall directly
return PendingIntents.
 */
class DefaultInstaller(
    context: Context
) : Installer(context) {

    override fun start() {
        Timber.v("[Install] Default apps installer is online")
        // No-op
    }

    override fun stop() {
        Timber.v("[Install] Default apps installer is offline")
        // No-op
    }

    override fun observeReady(): Observable<Boolean> {
        return Observable.just(true)
    }

    override fun installPackageInternal(
        localApkUri: Uri,
        packageName: String
    ) {
        val installIntent = Intent(context, DefaultInstallerActivity::class.java)
        // installIntent.addFlags(FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
        installIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        installIntent.action = ACTION_INSTALL_APP
        installIntent.putExtra(PROP_APP_FILE_URI, localApkUri)
        installIntent.putExtra(PROP_APP_PACKAGE_NAME, packageName)

        context.startActivity(installIntent)
    }

    override fun uninstallPackage(
        packageName: String
    ) {
        val uninstallIntent = Intent(context, DefaultInstallerActivity::class.java)
        uninstallIntent.action = ACTION_UNINSTALL_PACKAGE
        uninstallIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
        uninstallIntent.putExtra(PROP_APP_PACKAGE_NAME, packageName)

        context.startActivity(uninstallIntent)
    }

    override fun isReady(): Boolean = true
}