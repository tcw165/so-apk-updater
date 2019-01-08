package co.sodalabs.apkupdater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

private const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

class BootCompleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != QUICKBOOT_POWERON) return

        // Do nothing, this initializes the Application already
        Timber.i("BootCompleteReceiver: ${intent.action}")
    }
}
