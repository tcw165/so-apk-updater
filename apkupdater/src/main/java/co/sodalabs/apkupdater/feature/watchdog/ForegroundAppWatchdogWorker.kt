package co.sodalabs.apkupdater.feature.watchdog

import Packages.SPARKPOINT_PACKAGE_NAME
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_FOREGROUND_ACTIVITY
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_RESCUE_TIME
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.di.WorkerInjection
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject

internal const val PARAM_FORCEFULLY_START_LAUNCHER = "${BuildConfig.APPLICATION_ID}.work.forcefully_start_launcher"

class ForegroundAppWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

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

    private val uiHandler = Handler(Looper.getMainLooper())
    private val activityManager: ActivityManager by lazy {
        applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val forceStopPackageMethod: Method by lazy {
        activityManager::class.java.getDeclaredMethod("forceStopPackage", String::class.java)
    }

    override fun doWork(): Result {
        WorkerInjection.inject(this)
        Timber.v("[ForegroundAppWatchdog] Validating the foreground app...")

        val forcefullyStartLauncher = inputData.getBoolean(PARAM_FORCEFULLY_START_LAUNCHER, false)
        return if (forcefullyStartLauncher) {
            forcefullyStartLauncher()

            // Then start the periodic correction.
            uiHandler.post { watchdogLauncher.schedulePeriodicallyCorrection() }

            return Result.success()
        } else {
            correctLauncherAndSmartlyStart()
        }
    }

    private fun forcefullyStartLauncher() {
        try {
            if (sharedSettings.isUserSetupComplete()) {
                // Smartly set Sparkpoint player as default system launcher.
                val currentLauncherPackageName = systemLauncherUtil.getCurrentDefaultSystemLauncherPackageName()
                if (currentLauncherPackageName != SPARKPOINT_PACKAGE_NAME) {
                    Timber.v("[ForegroundAppWatchdog] Current launcher app, '$currentLauncherPackageName' is NOT Sparkpoint! So correct the launcher.")
                    systemLauncherUtil.setSodaLabsLauncherAsDefaultIfInstalled()
                }

                Timber.v("[ForegroundAppWatchdog] Start Sparkpoint launcher")

                systemLauncherUtil.startSodaLabsLauncherIfInstalled()
            } else {
                Timber.d("[ForegroundAppWatchdog] Validation is SKIPPED cause user-setup is incomplete.")
            }
        } catch (error: Throwable) {
            Timber.w(error, "[ForegroundAppWatchdog] Something is wrong...")
        }
    }

    private fun correctLauncherAndSmartlyStart(): Result {
        try {
            if (sharedSettings.isUserSetupComplete()) {
                // Only check after user-setup.
                val foregroundTaskInfo = activityManager.getRunningTasks(1)[0]
                val foregroundActivity = foregroundTaskInfo.topActivity
                val foregroundPackageName = foregroundActivity.packageName

                // Add foreground Activity component name to heartbeat metadata!
                updaterStateTracker.addStateMetadata(
                    mapOf(
                        KEY_FOREGROUND_ACTIVITY to "$foregroundPackageName/${foregroundActivity.className}"
                    )
                )

                // Smartly set Sparkpoint player as default system launcher.
                val currentLauncherPackageName = systemLauncherUtil.getCurrentDefaultSystemLauncherPackageName()
                if (currentLauncherPackageName != SPARKPOINT_PACKAGE_NAME) {
                    Timber.v("[ForegroundAppWatchdog] Forcefully set Sparkpoint as the default launcher, previously was '$currentLauncherPackageName'.")
                    systemLauncherUtil.setSodaLabsLauncherAsDefaultIfInstalled()
                }

                if (!foregroundPackageName.isFromSodalabs()) {
                    Timber.v("[ForegroundAppWatchdog] Current foreground app, '$foregroundPackageName', is NOT from SodaLabs, so force stop it and start the default SodaLabs launcher!")

                    forceStopPackageMethod.invoke(activityManager, foregroundPackageName)

                    systemLauncherUtil.startSodaLabsLauncherIfInstalled()

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

        return Result.success()
    }

    private fun String.isFromSodalabs(): Boolean {
        return this.contains(Regex("^co.sodalabs"))
    }
}