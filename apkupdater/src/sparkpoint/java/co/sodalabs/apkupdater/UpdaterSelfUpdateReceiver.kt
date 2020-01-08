package co.sodalabs.apkupdater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterService
import dagger.android.AndroidInjection
import timber.log.Timber

/**
 * This receiver serves for very particular purpose:
 * 1. There is another system component add/replace the updater; System won't
 *    know to start the UpdaterService.
 * 2. Developer manually load up the updater; System don't know what to do with
 *    it after the installation.
 */
class UpdaterSelfUpdateReceiver : BroadcastReceiver() {

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
                if (packageName == BuildConfig.APPLICATION_ID) {
                    Timber.v("[Updater] Start the UpdaterService on ${intent.action}!")

                    val engineServiceIntent = Intent(context, UpdaterService::class.java)
                    engineServiceIntent.action = IntentActions.ACTION_ENGINE_START
                    context.startService(engineServiceIntent)
                }
            }
        }
    }
}