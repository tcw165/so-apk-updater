package co.sodalabs.apkupdater

import Packages
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.apkupdater.feature.watchdog.IForegroundAppWatchdogLauncher
import dagger.android.AndroidInjection
import javax.inject.Inject

class SparkPointUpdatedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var foregroundAppWatchdogLauncher: IForegroundAppWatchdogLauncher

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        AndroidInjection.inject(this, context)

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageURI = intent.data ?: return
                val packageName = packageURI.schemeSpecificPart

                // Check given package name to see if it's the SparkPoint player.
                if (packageName == Packages.SPARKPOINT_PACKAGE_NAME) {
                    foregroundAppWatchdogLauncher.cancelPendingAndOnGoingValidation()
                    foregroundAppWatchdogLauncher.correctNowThenCheckPeriodically()
                }
            }
        }
    }
}