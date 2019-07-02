package co.sodalabs.updaterengine.exception

data class DownloadSizeUnknownException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download size for \"$packageName\" is unknown. URL is \"$downloadURL\"")