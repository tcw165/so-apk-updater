package co.sodalabs.apkupdater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MY_PACKAGE_REPLACED
import co.sodalabs.apkupdater.utils.ConfigHelper
import co.sodalabs.updaterengine.ApkUpdater

class SelfUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MY_PACKAGE_REPLACED) {
            ApkUpdater.updateConfig(ConfigHelper.getDefault(context))
        }
    }
}
