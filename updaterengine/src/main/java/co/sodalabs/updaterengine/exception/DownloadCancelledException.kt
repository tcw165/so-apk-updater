package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadCancelledException(
    val url: String
) : RuntimeException("Download for \"$url\" is cancelled.")