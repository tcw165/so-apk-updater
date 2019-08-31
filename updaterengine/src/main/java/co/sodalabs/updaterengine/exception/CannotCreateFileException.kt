package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep
import java.io.File

@Keep
data class CannotCreateFileException(
    val file: File
) : RuntimeException("Cannot create $file")