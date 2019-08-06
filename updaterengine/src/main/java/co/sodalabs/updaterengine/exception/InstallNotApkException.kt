package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class InstallNotApkException(
    val filePath: String
) : RuntimeException("$filePath is not a valid APK")