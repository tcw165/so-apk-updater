package co.sodalabs.updaterengine.feature.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
The default installer of F-Droid. It uses the normal Intents APIs of Android
to install apks. Its main inner workings are encapsulated in DefaultInstallerActivity.

This is installer requires user interaction and thus install/uninstall directly
return PendingIntents.
 */
class DefaultInstaller(
    context: Context
) : Installer(context) {

    override fun installPackageInternal(
        localUpdates: List<DownloadedUpdate>
    ) {
        val remainingUpdates: Queue<DownloadedUpdate> = LinkedList(localUpdates)
        val errors = mutableListOf<Throwable>()

        while (remainingUpdates.isNotEmpty()) {
            val countDownLatch = CountDownLatch(1)
            val update = remainingUpdates.poll()
            val installIntent = Intent(context, DefaultInstallerActivity::class.java)
            // val fileURI = Uri.fromFile(update.file)
            val fileURI = ApkFileProvider.fromFile(context, update.file)
            val packageName = update.fromUpdate.packageName
            installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            installIntent.action = DefaultInstallerActivity.ACTION_INSTALL_PACKAGE
            installIntent.putExtra(DefaultInstallerActivity.PROP_FILE_URI, fileURI)
            installIntent.putExtra(DefaultInstallerActivity.PROP_PACKAGE_NAME, packageName)

            val installReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    // val installComplete = intent.getBooleanExtra(DefaultInstallerActivity.PROP_RESULT_BOOLEAN)
                    countDownLatch.countDown()
                }
            }
            context.registerReceiver(installReceiver, IntentFilter(DefaultInstallerActivity.ACTION_INSTALL_PACKAGE))
            context.startActivity(installIntent)

            // Wait for response and then move on
            try {
                countDownLatch.await(Intervals.TIMEOUT_INSTALL_MIN, TimeUnit.MINUTES)
            } catch (error: Throwable) {
                errors.add(error)
                // TODO: Can we also stop Activity?
            } finally {
                context.unregisterReceiver(installReceiver)
            }
        }

        // Throw the errors (if they present) when everything is done.
        if (errors.isNotEmpty()) {
            throw CompositeException(errors)
        }
    }

    override fun uninstallPackage(
        packageNames: List<String>
    ) {
        val remainingUninstalls: Queue<String> = LinkedList(packageNames)
        val errors = mutableListOf<Throwable>()

        while (remainingUninstalls.isNotEmpty()) {
            val countDownLatch = CountDownLatch(1)
            val packageName = remainingUninstalls.poll()
            val uninstallIntent = Intent(context, DefaultInstallerActivity::class.java)
            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            uninstallIntent.action = DefaultInstallerActivity.ACTION_UNINSTALL_PACKAGE
            uninstallIntent.putExtra(DefaultInstallerActivity.PROP_PACKAGE_NAME, packageName)

            val installReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    countDownLatch.countDown()
                }
            }
            context.registerReceiver(installReceiver, IntentFilter(DefaultInstallerActivity.ACTION_UNINSTALL_PACKAGE))
            context.startActivity(uninstallIntent)

            // Wait for response and then move on
            try {
                countDownLatch.await(Intervals.TIMEOUT_INSTALL_MIN, TimeUnit.MINUTES)
            } catch (error: Throwable) {
                errors.add(error)
                // TODO: Can we also stop Activity?
            } finally {
                context.unregisterReceiver(installReceiver)
            }
        }

        // Throw the errors (if they present) when everything is done.
        if (errors.isNotEmpty()) {
            throw CompositeException(errors)
        }
    }
}