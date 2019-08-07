package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.data.AppUpdate

class DefaultUpdatesDownloader constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesDownloader {

    override fun downloadNow(
        updates: List<AppUpdate>
    ) {
        DownloadJobIntentService.downloadNow(context, updates)
    }

    override fun scheduleDownloadAfter(updates: List<AppUpdate>, afterMs: Long) {
        // TODO("not implemented")
    }

    override fun setDownloadCacheMaxSize(sizeInMB: Long) {
        TODO("not implemented")
    }
}