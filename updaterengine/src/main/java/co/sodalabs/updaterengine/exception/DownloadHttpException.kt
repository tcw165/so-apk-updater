package co.sodalabs.updaterengine.exception

data class DownloadHttpException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is failed. URL is \"$downloadURL\"")