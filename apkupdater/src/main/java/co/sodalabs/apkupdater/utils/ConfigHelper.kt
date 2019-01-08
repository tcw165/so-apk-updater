package co.sodalabs.apkupdater.utils

import android.content.Context
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.data.Apk
import timber.log.Timber

private const val ADVERTISEMENT_APP_PACKAGE_NAME = "co.sodalabs.sparkpoint"

object ConfigHelper {

    fun getDefault(context: Context): ApkUpdater.Config {
        return ApkUpdater.Config(context, BuildUtils.UPDATE_URL)
            .setAutoDownload(true)
            .setAutoInstall(true)
            .setPackageName(ADVERTISEMENT_APP_PACKAGE_NAME)
            .addCallback(object : ApkUpdater.OnUpdateAvailableCallback {
                override fun onUpdateAvailable(apk: Apk, updateMessage: String) {
                    Timber.d("onUpdateAvailable:\n$apk\n$updateMessage")
                }

                override fun onUpdateDownloaded(apk: Apk) {
                    Timber.d("onUpdateDownloaded:\n$apk")
                }

                override fun onUpdateDownloadFailed(apk: Apk, reason: String) {
                    Timber.d("onUpdateDownloadFailed:\n$apk\n$reason")
                }
            })
    }
}