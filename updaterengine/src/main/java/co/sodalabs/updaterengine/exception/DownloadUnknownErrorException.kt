package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DownloadUnknownErrorException(
    val errorCode: Int,
    val downloadURL: String
) : RuntimeException("Download error $errorCode for \"$downloadURL\"")