package co.sodalabs.updaterengine.exception

data class DownloadTimeoutException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is timeout. URL is \"$downloadURL\"")