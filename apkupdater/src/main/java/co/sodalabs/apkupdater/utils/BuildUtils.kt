package co.sodalabs.apkupdater.utils

import co.sodalabs.apkupdater.BuildConfig

/**
Utils class used for system interaction. Specifically useful for killing services.
 */
@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    val PACKAGES_TO_CHECK: Array<String> = BuildConfig.PACKAGES_TO_CHECK

    fun isDebug() = BuildConfig.BUILD_TYPE == "debug"

    fun isStaging() = BuildConfig.BUILD_TYPE == "staging"

    fun isRelease() = BuildConfig.BUILD_TYPE == "release"
}
