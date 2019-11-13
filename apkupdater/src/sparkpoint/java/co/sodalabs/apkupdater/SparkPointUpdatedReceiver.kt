package co.sodalabs.apkupdater

import Packages
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import timber.log.Timber

private const val RESTART_HOME_LAUNCHER = 0
private const val RESTART_DELAY_MILLIS = 600L

class SparkPointUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageURI = intent.data ?: return
                val packageName = packageURI.schemeSpecificPart

                // Check given package name to see if it's the SparkPoint player.
                if (packageName == Packages.SPARKPOINT_PACKAGE_NAME) {
                    // Use Handler to debounce the two consecutive events.
                    uiHandler.removeMessages(RESTART_HOME_LAUNCHER)
                    uiHandler.sendMessageDelayed(Message.obtain(uiHandler, RESTART_HOME_LAUNCHER, context), RESTART_DELAY_MILLIS)
                }
            }
        }
    }

    private val uiHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != RESTART_HOME_LAUNCHER) return

            val context = msg.obj as Context
            restartPlayer(context)
        }
    }

    private fun restartPlayer(
        context: Context
    ) {
        Timber.v("SparkPoint player is just replaced, restart the HOME launcher...")
        try {
            // Some install fails and therefore cannot restart the app.
            // See https://app.clubhouse.io/soda/story/869/updater-crashes-when-the-sparkpoint-player-updates
            context.startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        } catch (error: Throwable) {
            Timber.w(error)
        }
    }
}