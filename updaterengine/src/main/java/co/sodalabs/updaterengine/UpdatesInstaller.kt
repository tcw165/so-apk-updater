package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate

interface UpdatesInstaller {
    fun installAppUpdateNow(downloadedUpdates: List<DownloadedAppUpdate>)
    fun installFirmwareUpdateNow(downloadedUpdate: DownloadedFirmwareUpdate)
    fun scheduleInstallAppUpdate(downloadedUpdates: List<DownloadedAppUpdate>, triggerAtMillis: Long)
    fun scheduleInstallFirmwareUpdate(downloadedUpdate: DownloadedFirmwareUpdate, triggerAtMillis: Long)
    fun cancelPendingInstalls()

    fun uninstallAppNow(packageNames: List<String>)
    fun scheduleUninstallApp(packageNames: List<String>, triggerAtMillis: Long)
}