package co.sodalabs.privilegedinstaller.utils

import co.sodalabs.privilegedinstaller.BuildConfig

@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    const val TYPE_DEBUG = "debug"
    const val TYPE_PRE_RELEASE = "preRelease"
    const val TYPE_RELEASE = "release"

    fun isDebug() = BuildConfig.BUILD_TYPE == TYPE_DEBUG
    fun isPreRelease() = BuildConfig.BUILD_TYPE == TYPE_PRE_RELEASE
    fun isRelease() = BuildConfig.BUILD_TYPE == TYPE_RELEASE
}