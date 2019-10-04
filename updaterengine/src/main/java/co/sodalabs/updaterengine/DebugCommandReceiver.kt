package co.sodalabs.updaterengine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.sodalabs.updaterengine.extension.showShortToast
import timber.log.Timber
import java.io.File

class DebugCommandReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            DebugIntentActions.ACTION_INSTALL_FULL_FIRMWARE_UPDATE -> installFirmwareUpdate(context, intent, isIncremental = false)
            DebugIntentActions.ACTION_INSTALL_INCREMENTAL_FIRMWARE_UPDATE -> installFirmwareUpdate(context, intent, isIncremental = true)
        }
    }

    private fun installFirmwareUpdate(
        context: Context,
        intent: Intent,
        isIncremental: Boolean
    ) {
        try {
            val filePath = intent.getStringExtra(DebugIntentActions.PROP_FILE)
            val file = File(filePath)
            UpdaterService.debugInstallFirmwareUpdate(context, file, isIncremental)
        } catch (error: Throwable) {
            Timber.e(error)
            context.showShortToast("Hey dev, you may forget the 'file' payload. Error: $error")
        }
    }
}