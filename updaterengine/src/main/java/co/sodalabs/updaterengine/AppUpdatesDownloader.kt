package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate

interface AppUpdatesDownloader {
    fun downloadNow(updates: List<AppUpdate>)
    fun scheduleDownloadAfter(updates: List<AppUpdate>, afterMs: Long)
    fun setDownloadCacheMaxSize(sizeInMB: Long)
}