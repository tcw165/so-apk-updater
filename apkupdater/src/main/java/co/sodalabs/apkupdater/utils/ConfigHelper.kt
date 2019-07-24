package co.sodalabs.apkupdater.utils

import android.content.Context
import co.sodalabs.updaterengine.ApkUpdaterConfig
import java.util.concurrent.TimeUnit

object ConfigHelper {

    fun generateDefault(context: Context): ApkUpdaterConfig {
        val hostPackageName = context.packageName

        return if (BuildUtils.isDebug() || BuildUtils.isStaging()) {
            ApkUpdaterConfig(
                hostPackageName = hostPackageName,
                // packageNames = listOf(hostPackageName, *BuildUtils.PACKAGES_TO_CHECK)
                packageNames = listOf(*BuildUtils.PACKAGES_TO_CHECK),
                checkInterval = TimeUnit.MINUTES.toMillis(1),
                heartBeatInterval = TimeUnit.SECONDS.toMillis(25)
            )
        } else {
            ApkUpdaterConfig(
                hostPackageName = hostPackageName,
                // packageNames = listOf(hostPackageName, *BuildUtils.PACKAGES_TO_CHECK)
                packageNames = listOf(*BuildUtils.PACKAGES_TO_CHECK),
                checkInterval = TimeUnit.DAYS.toMillis(1),
                heartBeatInterval = TimeUnit.MINUTES.toMillis(5)
            )
        }
    }
}