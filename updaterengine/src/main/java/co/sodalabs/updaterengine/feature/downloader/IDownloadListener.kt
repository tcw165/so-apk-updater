package co.sodalabs.updaterengine.feature.downloader

@Deprecated("Soon would be replaced by persistent updater state")
internal interface IDownloadListener {
    fun onDownloading(urlIndex: Int, progressPercentage: Int, currentBytes: Long, totalBytes: Long)
}