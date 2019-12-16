package co.sodalabs.apkupdater.feature.homeCorrector

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.di.WorkerInjection
import javax.inject.Inject

class HomeActivityLauncherWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var systemLauncherUtil: ISystemLauncherUtil

    override fun doWork(): Result {
        WorkerInjection.inject(this)

        systemLauncherUtil.setSodaLabsLauncherAsDefaultIfInstalled()
        if (sharedSettings.isUserSetupComplete()) {
            systemLauncherUtil.startSodaLabsLauncherIfInstalled()
        }

        return Result.success()
    }
}