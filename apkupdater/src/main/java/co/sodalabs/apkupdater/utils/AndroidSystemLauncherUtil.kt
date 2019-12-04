package co.sodalabs.apkupdater.utils

import android.content.Context
import android.content.Intent
import co.sodalabs.apkupdater.ISystemLauncherUtil
import javax.inject.Inject

class AndroidSystemLauncherUtil @Inject constructor(
    private val context: Context
) : ISystemLauncherUtil {

    override fun startSystemLauncher() {
        // Some install fails and therefore cannot restart the app.
        // See https://app.clubhouse.io/soda/story/869/updater-crashes-when-the-sparkpoint-player-updates
        context.startActivity(Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}