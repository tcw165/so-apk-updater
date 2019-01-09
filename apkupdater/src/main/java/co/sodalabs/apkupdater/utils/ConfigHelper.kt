package co.sodalabs.apkupdater.utils

import android.content.Context
import co.sodalabs.updaterengine.ApkUpdater

private const val ADVERTISEMENT_APP_PACKAGE_NAME = "co.sodalabs.sparkpoint"

object ConfigHelper {

    fun getDefault(context: Context): ApkUpdater.Config {
        return ApkUpdater.Config(context, BuildUtils.UPDATE_URL)
            .setPackageName(ADVERTISEMENT_APP_PACKAGE_NAME)
    }
}