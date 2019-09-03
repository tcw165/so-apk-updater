package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadSizeNotFoundException(
    val url: String
) : RuntimeException("Cannot get the content length from \"$url\"")