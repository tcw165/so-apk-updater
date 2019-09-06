package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.data.AppUpdate
import javax.inject.Inject

class DefaultUpdatesDownloader @Inject constructor(
    private val context: Context
) : AppUpdatesDownloader {

    override fun downloadNow(
        updates: List<AppUpdate>
    ) {
        DownloadJobIntentService.downloadNow(context, updates)
    }

    override fun scheduleDownloadAfter(
        updates: List<AppUpdate>,
        afterMs: Long
    ) {
        TODO("not implemented")
    }

    override fun setDownloadCacheMaxSize(
        sizeInMB: Long
    ) {
        TODO("not implemented")
    }
}