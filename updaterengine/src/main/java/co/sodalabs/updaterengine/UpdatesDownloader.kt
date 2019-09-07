package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate

interface UpdatesDownloader {
    fun downloadNow(updates: List<AppUpdate>)
    fun scheduleDelayedDownload(updates: List<AppUpdate>, delayMillis: Long)
    fun cancelDownloads()

    fun setDownloadCacheMaxSize(sizeInMB: Long)
}