package co.sodalabs.apkupdater.utils

import android.content.Context
import co.sodalabs.updaterengine.ApkUpdater

object ConfigHelper {

    fun getDefault(context: Context): ApkUpdater.Config {
        return ApkUpdater.Config(context, BuildUtils.UPDATE_URL)
            .setPackageName(BuildUtils.PACKAGE_TO_CHECK)
    }
}