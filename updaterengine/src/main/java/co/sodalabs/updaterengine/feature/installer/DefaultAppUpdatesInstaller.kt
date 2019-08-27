package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.data.DownloadedUpdate
import javax.inject.Inject

class DefaultAppUpdatesInstaller @Inject constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesInstaller {

    override fun install(
        downloadedUpdates: List<DownloadedUpdate>
    ) {
        InstallerJobIntentService.installNow(context, downloadedUpdates)
    }

    override fun installFromDiskCache() {
        InstallerJobIntentService.installFromDiskCacheNow(context)
    }

    override fun scheduleInstall(
        downloadedUpdates: List<DownloadedUpdate>,
        triggerAtMillis: Long
    ) {
        InstallerJobIntentService.scheduleInstall(context, downloadedUpdates, triggerAtMillis)
    }
}