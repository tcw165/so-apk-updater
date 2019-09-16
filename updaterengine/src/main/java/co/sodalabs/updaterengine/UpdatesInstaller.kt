package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate

interface UpdatesInstaller {
    fun installAppUpdateNow(downloadedUpdates: List<DownloadedAppUpdate>)
    fun installFirmwareUpdateNow(downloadedUpdates: List<DownloadedFirmwareUpdate>)
    fun scheduleInstallAppUpdate(downloadedUpdates: List<DownloadedAppUpdate>, triggerAtMillis: Long)
    fun scheduleInstallFirmwareUpdate(downloadedUpdates: List<DownloadedFirmwareUpdate>, triggerAtMillis: Long)
    fun cancelPendingInstalls()
}