package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import co.sodalabs.updaterengine.UpdatesInstaller
import co.sodalabs.updaterengine.data.DownloadedUpdate
import javax.inject.Inject

class DefaultUpdatesInstaller @Inject constructor(
    private val context: Context
) : UpdatesInstaller {

    override fun installNow(
        downloadedUpdates: List<DownloadedUpdate>
    ) {
        InstallerJobIntentService.installNow(context, downloadedUpdates)
    }

    override fun scheduleDelayedInstall(
        downloadedUpdates: List<DownloadedUpdate>,
        triggerAtMillis: Long
    ) {
        InstallerJobIntentService.scheduleInstall(context, downloadedUpdates, triggerAtMillis)
    }

    override fun cancelPendingInstalls() {
        InstallerJobIntentService.cancelPendingInstalls(context)
    }
}