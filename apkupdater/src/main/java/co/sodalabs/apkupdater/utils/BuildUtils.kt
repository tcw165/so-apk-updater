package co.sodalabs.apkupdater.utils

import co.sodalabs.apkupdater.BuildConfig

@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    val PACKAGES_TO_CHECK: Array<String> = BuildConfig.PACKAGES_TO_CHECK

    const val TYPE_DEBUG = "debug"
    const val TYPE_STAGING = "staging"
    const val TYPE_RELEASE = "release"

    fun isDebug() = BuildConfig.BUILD_TYPE == TYPE_DEBUG
    fun isStaging() = BuildConfig.BUILD_TYPE == TYPE_STAGING
    fun isRelease() = BuildConfig.BUILD_TYPE == TYPE_RELEASE
}