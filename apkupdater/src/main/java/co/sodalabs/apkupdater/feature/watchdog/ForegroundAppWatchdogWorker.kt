package co.sodalabs.apkupdater.feature.watchdog

import android.app.ActivityManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_RESCUE_TIME
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.di.WorkerInjection
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ForegroundAppWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        val canRun = AtomicBoolean(true)
    }

    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var watchdogLauncher: IForegroundAppWatchdogLauncher
    @Inject
    lateinit var timeUtil: ITimeUtil
    @Inject
    lateinit var systemLauncherUtil: ISystemLauncherUtil
    @Inject
    lateinit var updaterStateTracker: IUpdaterStateTracker

    private val activityManager: ActivityManager by lazy {
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val forceStopPackageMethod: Method by lazy {
        activityManager::class.java.getDeclaredMethod("forceStopPackage", String::class.java)
    }

    override fun doWork(): Result {
        WorkerInjection.inject(this)
        Timber.v("[ForegroundAppWatchdog] Validating the foreground app...")

        try {
            if (sharedSettings.isUserSetupComplete()) {
                // Only check after user-setup.
                val foregroundTaskInfo = activityManager.getRunningTasks(1)[0]
                val foregroundPackageName = foregroundTaskInfo.topActivity.packageName
                if (!canRun.get()) throw InterruptedException()

                if (!foregroundPackageName.isFromSodalabs()) {
                    Timber.v("[ForegroundAppWatchdog] Current foreground app, '$foregroundPackageName', is NOT from SodaLabs, so force stop it and start the default SodaLabs launcher!")

                    forceStopPackageMethod.invoke(activityManager, foregroundPackageName)
                    if (!canRun.get()) throw InterruptedException()

                    systemLauncherUtil.startSystemLauncher()

                    // Add rescue time to to heartbeat metadata!
                    updaterStateTracker.addStateMetadata(
                        mapOf(
                            KEY_RESCUE_TIME to timeUtil.now().atZone(ZoneId.systemDefault()).toString()
                        )
                    )
                } else {
                    Timber.v("[ForegroundAppWatchdog] Current foreground app, '$foregroundPackageName', is from SodaLabs!")
                }
            } else {
                Timber.d("[ForegroundAppWatchdog] Validation is SKIPPED cause user-setup is incomplete.")
            }
        } catch (error: Throwable) {
            Timber.w(error, "[ForegroundAppWatchdog] Something is wrong...")
        }

        // Most importantly, schedule next validation.
        watchdogLauncher.scheduleForegroundProcessValidation()

        return Result.success()
    }

    private fun String.isFromSodalabs(): Boolean {
        return this.contains(Regex("^co.sodalabs"))
    }
}