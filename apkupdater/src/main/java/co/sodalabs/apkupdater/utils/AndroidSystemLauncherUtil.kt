package co.sodalabs.apkupdater.utils

import Packages.SPARKPOINT_PACKAGE_NAME
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import co.sodalabs.apkupdater.ISystemLauncherUtil
import timber.log.Timber
import javax.inject.Inject

class AndroidSystemLauncherUtil @Inject constructor(
    private val context: Context
) : ISystemLauncherUtil {

    private val packageManager: PackageManager by lazy { context.packageManager }

    override fun startSystemLauncher() {
        Timber.v("[LauncherUtil] Start the system launcher via HOME Intent.")
        // Some install fails and therefore cannot restart the app.
        // See https://app.clubhouse.io/soda/story/869/updater-crashes-when-the-sparkpoint-player-updates
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    override fun startSodaLabsLauncherIfPresent() {
        val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(SPARKPOINT_PACKAGE_NAME)
        launchIntent?.let {
            Timber.v("[LauncherUtil] Start the SodaLabs launcher via package name.")
            context.startActivity(it.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } ?: kotlin.run {
            Timber.w("[LauncherUtil] Couldn't find SodaLabs launcher, so do nothing")
        }
    }
}