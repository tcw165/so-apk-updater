package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import co.sodalabs.updaterengine.UpdatesInstaller
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import javax.inject.Inject

class DefaultUpdatesInstaller @Inject constructor(
    private val context: Context
) : UpdatesInstaller {

    override fun installAppUpdateNow(
        downloadedUpdates: List<DownloadedAppUpdate>
    ) {
        InstallerJobIntentService.installAppUpdateNow(context, downloadedUpdates)
    }

    override fun installFirmwareUpdateNow(
        downloadedUpdate: DownloadedFirmwareUpdate
    ) {
        InstallerJobIntentService.installFirmwareUpdateNow(context, downloadedUpdate)
    }

    override fun scheduleInstallAppUpdate(
        downloadedUpdates: List<DownloadedAppUpdate>,
        triggerAtMillis: Long
    ) {
        InstallerJobIntentService.scheduleInstallAppUpdate(context, downloadedUpdates, triggerAtMillis)
    }

    override fun scheduleInstallFirmwareUpdate(
        downloadedUpdate: DownloadedFirmwareUpdate,
        triggerAtMillis: Long
    ) {
        InstallerJobIntentService.scheduleInstallFirmwareUpdate(context, downloadedUpdate, triggerAtMillis)
    }

    override fun cancelPendingInstalls() {
        InstallerJobIntentService.cancelPendingInstalls(context)
    }
}