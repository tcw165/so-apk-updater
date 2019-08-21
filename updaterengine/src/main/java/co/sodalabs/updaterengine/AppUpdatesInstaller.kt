package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.DownloadedUpdate

interface AppUpdatesInstaller {
    fun install(downloadedUpdates: List<DownloadedUpdate>)
    fun installFromDiskCache()
    fun scheduleInstall(downloadedUpdates: List<DownloadedUpdate>, triggerAtMillis: Long)
}