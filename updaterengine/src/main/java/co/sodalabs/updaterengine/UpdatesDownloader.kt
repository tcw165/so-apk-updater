package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate

interface UpdatesDownloader {
    fun downloadAppUpdateNow(updates: List<AppUpdate>)
    fun downloadFirmwareUpdateNow(update: FirmwareUpdate)
    fun scheduleDownloadAppUpdate(updates: List<AppUpdate>, triggerAtMillis: Long)
    fun scheduleDownloadFirmwareUpdate(update: FirmwareUpdate, triggerAtMillis: Long)
    /**
     * Cancel any pending download tasks or the in-progress download.
     */
    fun cancelPendingAndWipDownloads()

    fun setDownloadCacheMaxSize(sizeInMB: Long)
}