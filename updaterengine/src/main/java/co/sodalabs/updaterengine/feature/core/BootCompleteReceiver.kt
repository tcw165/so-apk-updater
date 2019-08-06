package co.sodalabs.updaterengine.feature.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.updaterengine.IntentActions
import timber.log.Timber

private const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != QUICKBOOT_POWERON) return

        // Do nothing, this initializes the Application already
        Timber.v("Start the updater engine: ${intent.action}")

        val engineServiceIntent = Intent(context, AppUpdaterService::class.java)
        engineServiceIntent.action = IntentActions.ACTION_ENGINE_START
        context.startService(engineServiceIntent)
    }
}