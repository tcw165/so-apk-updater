package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadFileIOException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Failed to write file for download of \"$packageName\" (URL is \"$downloadURL\")")