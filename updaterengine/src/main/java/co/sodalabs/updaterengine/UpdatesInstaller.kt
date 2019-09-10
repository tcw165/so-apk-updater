package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.DownloadedUpdate

interface UpdatesInstaller {
    fun installNow(downloadedUpdates: List<DownloadedUpdate>)
    fun scheduleDelayedInstall(downloadedUpdates: List<DownloadedUpdate>, triggerAtMillis: Long)
    fun cancelPendingInstalls()
}