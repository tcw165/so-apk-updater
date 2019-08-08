package co.sodalabs.privilegedinstaller.utils

import co.sodalabs.privilegedinstaller.BuildConfig

@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    const val TYPE_DEBUG = "debug"
    const val TYPE_STAGING = "staging"
    const val TYPE_RELEASE = "release"

    fun isDebug() = BuildConfig.BUILD_TYPE == TYPE_DEBUG
    fun isStaging() = BuildConfig.BUILD_TYPE == TYPE_STAGING
    fun isRelease() = BuildConfig.BUILD_TYPE == TYPE_RELEASE
}