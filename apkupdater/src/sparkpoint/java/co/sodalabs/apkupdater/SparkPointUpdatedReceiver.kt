package co.sodalabs.apkupdater

import Packages
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.apkupdater.feature.homeCorrector.IHomeCorrectorLauncher
import dagger.android.AndroidInjection
import javax.inject.Inject

private const val START_PROCESS_DELAY_MILLIS = 3000L

class SparkPointUpdatedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var homeCorrectorLauncher: IHomeCorrectorLauncher

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
                    homeCorrectorLauncher.scheduleStartingSodaLabsLauncher(START_PROCESS_DELAY_MILLIS)
                }
            }
        }
    }
}