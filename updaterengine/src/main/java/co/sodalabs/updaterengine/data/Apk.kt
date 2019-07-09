package co.sodalabs.updaterengine.data

import androidx.annotation.Keep
import java.io.File

@Keep
data class Apk(
    val file: File,
    val fromUpdate: AppUpdate
) {

    /**
     * Default to assuming apk if apkName is null since that has always been
     * what we had.
     *
     * @return true if this is an apk instead of a non-apk/media file
     */
    fun isApk(): Boolean {
        return true
    }
}