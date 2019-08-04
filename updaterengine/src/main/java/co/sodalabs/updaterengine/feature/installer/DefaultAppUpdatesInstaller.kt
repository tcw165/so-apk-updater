package co.sodalabs.updaterengine.feature.installer

import android.content.Context
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.data.DownloadedUpdate

class DefaultAppUpdatesInstaller constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesInstaller {

    override fun install(
        downloadedUpdates: List<DownloadedUpdate>
    ) {
        InstallerJobIntentService.install(context, downloadedUpdates)
    }

    override fun scheduleRecurringInstall(
        startTimeInDay: Long,
        endTimeInDay: Long
    ) {
        // TODO("not implemented")
    }
}