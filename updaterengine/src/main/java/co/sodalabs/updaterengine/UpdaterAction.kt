package co.sodalabs.updaterengine

import androidx.annotation.Keep
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate

@Keep
sealed class UpdaterAction {

    data class ScheduleUpdateCheck(
        val interval: Long,
        val periodic: Boolean
    ) : UpdaterAction()

    data class DownloadUpdates(
        val updates: List<AppUpdate>
    ) : UpdaterAction()

    data class InstallApps(
        val apps: List<Apk>
    ) : UpdaterAction()
}