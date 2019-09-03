package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep
import java.io.File

@Keep
data class DownloadInvalidFileSizeException(
    val file: File,
    val fileSize: Long,
    val expectedSize: Long
) : RuntimeException("Expect $expectedSize bytes but the \"$file\" size is $fileSize bytes.")