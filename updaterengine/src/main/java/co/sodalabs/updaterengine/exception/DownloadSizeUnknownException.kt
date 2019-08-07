package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadSizeUnknownException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download size for \"$packageName\" is unknown. URL is \"$downloadURL\"")