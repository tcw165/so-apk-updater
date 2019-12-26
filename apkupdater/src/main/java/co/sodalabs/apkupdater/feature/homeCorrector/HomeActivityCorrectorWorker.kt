package co.sodalabs.apkupdater.feature.homeCorrector

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.di.WorkerInjection
import javax.inject.Inject

class HomeActivityCorrectorWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var systemLauncherUtil: ISystemLauncherUtil

    override fun doWork(): Result {
        WorkerInjection.inject(this)

        // Note: Only set the HOME Activity without launching it, otherwise OOBE
        // might be disrupted.
        systemLauncherUtil.setSodaLabsLauncherAsDefaultIfInstalled()

        // If you really need to do something, please account for the user-setup-
        // complete boolean.
        // if (sharedSettings.isUserSetupComplete()) {
        // }

        return Result.success()
    }
}