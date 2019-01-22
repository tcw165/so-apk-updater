package co.sodalabs.apkupdater.utils

import co.sodalabs.apkupdater.BuildConfig

/**
Utils class used for system interaction. Specifically useful for killing services.
 */
@Suppress("TooGenericExceptionCaught")
object BuildUtils {

    const val UPDATE_URL: String = BuildConfig.BASE_URL

    // FIXME: Support multiple packages
    val PACKAGE_TO_CHECK: String by lazy { PACKAGES_TO_CHECK.first() }
    private val PACKAGES_TO_CHECK: Array<String> by lazy { BuildConfig.PACKAGES_TO_CHECK }

    fun isDebug() = BuildConfig.BUILD_TYPE == "debug"

    fun isStaging() = BuildConfig.BUILD_TYPE == "staging"

    fun isRelease() = BuildConfig.BUILD_TYPE == "release"
}
