package co.sodalabs.apkupdater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.apkupdater.feature.watchdog.IForegroundAppWatchdogLauncher
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

class WorkOnAppLaunchInitializer : BroadcastReceiver() {

    companion object {
        internal const val UPDATER_LAUNCH = "sodalabs.intent.action.updater_launch"
    }

    @Inject
    lateinit var foregroundAppWatchdogLauncher: IForegroundAppWatchdogLauncher

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        AndroidInjection.inject(this, context)

        if (intent.action == UPDATER_LAUNCH) {
            Timber.v("[WorkOnLaunch] Schedule the works necessary for on-launching (Note: Not on-boot)...")

            foregroundAppWatchdogLauncher.cancelPendingAndOnGoingValidation()
            foregroundAppWatchdogLauncher.correctNowThenCheckPeriodically()
        }
    }
}