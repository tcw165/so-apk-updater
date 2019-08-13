package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadProgressTooLargeException(
    val downloadURL: String,
    val downloadProgress: Int
) : RuntimeException("Download for \"$downloadURL\" has progress, $downloadProgress, larger than 100")