package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadTimeoutException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is timeout. URL is \"$downloadURL\"")