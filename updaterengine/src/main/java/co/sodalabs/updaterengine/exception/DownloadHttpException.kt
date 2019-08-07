package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadHttpException(
    val packageName: String,
    val downloadURL: String
) : RuntimeException("Download for \"$packageName\" is failed. URL is \"$downloadURL\"")