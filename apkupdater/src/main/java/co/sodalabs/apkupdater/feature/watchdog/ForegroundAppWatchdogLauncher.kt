package co.sodalabs.apkupdater.feature.watchdog

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_FOREGROUND_ACTIVITY
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_NEXT_CHECK_TIME
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.extension.ensureMainThread
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val UNIQUE_WORK_NAME = "validate_foreground_app"

private const val MIN_DELAY_MILLIS = BuildConfig.LAUNCHER_WATCHDOG_MIN_INTERVAL_SECONDS * 1000L
private const val MAX_DELAY_MILLIS = BuildConfig.LAUNCHER_WATCHDOG_MAX_INTERVAL_SECONDS * 1000L

class ForegroundAppWatchdogLauncher @Inject constructor(
    private val context: Context,
    private val timeUtil: ITimeUtil,
    private val updaterStateTracker: IUpdaterStateTracker
) : IForegroundAppWatchdogLauncher {

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    override fun correctNowThenCheckPeriodically() {
        ensureMainThread()

        Timber.d("[ForegroundAppWatchdog] Schedule one-shot foreground app correction.")

        val workData = Data.Builder()
            .putBoolean(PARAM_FORCEFULLY_START_LAUNCHER, true)
            .build()
        val workConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(Intervals.DELAY_ONE_SECOND, TimeUnit.MILLISECONDS)
            .build()
        val workRequest = OneTimeWorkRequest
            .Builder(ForegroundAppWatchdogWorker::class.java)
            .setConstraints(workConstraints)
            .setInitialDelay(Intervals.DELAY_ONE_SECOND, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .build()

        workManager.enqueueUniqueWork(
            // Note: We intentionally use the same name for the one-shot and
            // periodic work to avoid race condition. From there, the one-shot
            // work has to be followed by a periodic work!
            UNIQUE_WORK_NAME,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun schedulePeriodicallyCorrection() {
        ensureMainThread()

        Timber.d("[ForegroundAppWatchdog] Schedule periodical foreground app correction.")

        val workData = Data.Builder()
            .putBoolean(PARAM_FORCEFULLY_START_LAUNCHER, false)
            .build()
        val workConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(MAX_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        val workRequest = PeriodicWorkRequest
            .Builder(ForegroundAppWatchdogWorker::class.java, MIN_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(workConstraints)
            .setInitialDelay(MIN_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setInputData(workData)
            .build()

        // Add next check time to to heartbeat metadata!
        updaterStateTracker.addStateMetadata(
            mapOf(
                KEY_NEXT_CHECK_TIME to timeUtil.now().plusMillis(MIN_DELAY_MILLIS).atZone(ZoneId.systemDefault()).toString(),
                KEY_FOREGROUND_ACTIVITY to "n/a"
            )
        )

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    override fun cancelPendingAndOnGoingValidation() {
        ensureMainThread()

        Timber.d("[ForegroundAppWatchdog] Cancel all the pending foreground app correction.")

        // Cancel any pending work.
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}