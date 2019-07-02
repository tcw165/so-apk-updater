package co.sodalabs.updaterengine.exception

data class DownloadUnknownErrorException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is failed by unknown error code. URL is \"$downloadURL\"")