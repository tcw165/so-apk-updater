package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.DownloadedUpdate

interface AppUpdatesInstaller {
    fun install(downloadedUpdates: List<DownloadedUpdate>)
    fun scheduleRecurringInstall(startTimeInDay: Long, endTimeInDay: Long)
}