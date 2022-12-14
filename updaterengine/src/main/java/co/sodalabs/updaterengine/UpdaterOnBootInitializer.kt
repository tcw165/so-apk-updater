package co.sodalabs.updaterengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

private const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

class UpdaterOnBootInitializer : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != QUICKBOOT_POWERON) return

        Timber.v("[Updater] Start the UpdaterService on boot!")
        val engineServiceIntent = Intent(context, UpdaterService::class.java)
        engineServiceIntent.action = IntentActions.ACTION_ENGINE_START
        context.startService(engineServiceIntent)
    }
}