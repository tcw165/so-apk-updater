package co.sodalabs.updaterengine.exception

data class DownloadCancelledException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is cancelled. URL is \"$downloadURL\"")