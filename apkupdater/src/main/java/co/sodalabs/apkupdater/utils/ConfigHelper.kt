package co.sodalabs.apkupdater.utils

import android.content.Context
import co.sodalabs.updaterengine.ApkUpdaterConfig

object ConfigHelper {

    fun generateDefault(context: Context): ApkUpdaterConfig {
        val hostPackageName = context.packageName

        return ApkUpdaterConfig(
            hostPackageName = hostPackageName,
            // packageNames = listOf(hostPackageName, *BuildUtils.PACKAGES_TO_CHECK)
            packageNames = listOf(*BuildUtils.PACKAGES_TO_CHECK)
        )
    }
}