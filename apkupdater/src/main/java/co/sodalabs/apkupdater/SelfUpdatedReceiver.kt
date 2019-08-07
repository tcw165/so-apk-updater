package co.sodalabs.apkupdater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED

class SelfUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MY_PACKAGE_REPLACED) {
            // FIXME
            // ApkUpdater.updateConfig(ConfigHelper.generateUpdaterConfig(context))
        }
    }
}