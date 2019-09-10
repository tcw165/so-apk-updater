package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import co.sodalabs.updaterengine.UpdatesDownloader
import co.sodalabs.updaterengine.data.AppUpdate
import javax.inject.Inject

class DefaultUpdatesDownloader @Inject constructor(
    private val context: Context
) : UpdatesDownloader {

    override fun downloadNow(
        updates: List<AppUpdate>
    ) {
        DownloadJobIntentService.downloadNow(context, updates)
    }

    override fun scheduleDelayedDownload(
        updates: List<AppUpdate>,
        delayMillis: Long
    ) {
        TODO("not implemented")
    }

    override fun cancelPendingAndWipDownloads() {
        DownloadJobIntentService.cancelDownload()
    }

    override fun setDownloadCacheMaxSize(
        sizeInMB: Long
    ) {
        TODO("not implemented")
    }
}