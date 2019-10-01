package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import co.sodalabs.updaterengine.UpdatesDownloader
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import javax.inject.Inject

class DefaultUpdatesDownloader @Inject constructor(
    private val context: Context
) : UpdatesDownloader {

    override fun downloadAppUpdateNow(
        updates: List<AppUpdate>
    ) {
        DownloadJobIntentService.downloadAppUpdateNow(context, updates)
    }

    override fun scheduleDownloadAppUpdate(
        updates: List<AppUpdate>,
        triggerAtMillis: Long
    ) {
        DownloadJobIntentService.scheduleDownloadAppUpdate(context, updates, triggerAtMillis)
    }

    override fun downloadFirmwareUpdateNow(
        update: FirmwareUpdate
    ) {
        DownloadJobIntentService.downloadFirmwareUpdateNow(context, update)
    }

    override fun scheduleDownloadFirmwareUpdate(
        update: FirmwareUpdate,
        triggerAtMillis: Long
    ) {
        DownloadJobIntentService.scheduleDownloadFirmwareUpdate(context, update, triggerAtMillis)
    }

    override fun cancelPendingAndWipDownloads() {
        DownloadJobIntentService.cancelDownload(context)
    }

    override fun setDownloadCacheMaxSize(
        sizeInMB: Long
    ) {
        TODO("not implemented")
    }
}