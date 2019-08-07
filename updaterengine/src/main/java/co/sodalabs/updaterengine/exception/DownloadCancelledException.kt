package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadCancelledException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is cancelled. URL is \"$downloadURL\"")