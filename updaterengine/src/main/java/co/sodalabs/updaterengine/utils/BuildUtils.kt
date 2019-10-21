package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.BuildConfig

/**
Utils class used for system interaction. Specifically useful for killing services.
 */
@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    fun isDebug() = BuildConfig.BUILD_TYPE == "debug"
    fun isPreRelease() = BuildConfig.BUILD_TYPE == "preRelease"
    fun isRelease() = BuildConfig.BUILD_TYPE == "release"
}