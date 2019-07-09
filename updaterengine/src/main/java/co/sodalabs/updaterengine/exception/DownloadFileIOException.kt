package co.sodalabs.updaterengine.exception

data class DownloadFileIOException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Failed to write file for download of \"$packageName\" (URL is \"$downloadURL\")")