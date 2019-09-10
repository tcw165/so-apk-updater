package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate

interface UpdatesDownloader {
    fun downloadNow(updates: List<AppUpdate>)
    fun scheduleDelayedDownload(updates: List<AppUpdate>, delayMillis: Long)
    /**
     * Cancel any pending download tasks or the in-progress download.
     */
    fun cancelPendingAndWipDownloads()

    fun setDownloadCacheMaxSize(sizeInMB: Long)
}