package co.sodalabs.apkupdater

import Packages
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import dagger.android.AndroidInjection
import timber.log.Timber
import javax.inject.Inject

private const val RESTART_HOME_LAUNCHER = 0
private const val RESTART_DELAY_MILLIS = 600L

class SparkPointUpdatedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var systemLauncherUtil: ISystemLauncherUtil

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

            restartPlayer()
        }
    }

    private fun restartPlayer() {
        Timber.v("SparkPoint player is just replaced, restart the HOME launcher...")
        try {
            systemLauncherUtil.startSystemLauncher()
        } catch (error: Throwable) {
            Timber.w(error)
        }
    }
}