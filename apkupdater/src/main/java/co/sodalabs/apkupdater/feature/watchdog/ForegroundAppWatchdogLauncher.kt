package co.sodalabs.apkupdater.feature.watchdog

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogMetadata.KEY_NEXT_CHECK_TIME
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import org.threeten.bp.ZoneId
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val UNIQUE_WORK_NAME = "validate_foreground_app"

private const val MIN_DELAY_MILLIS = BuildConfig.LAUNCHER_WATCHDOG_MIN_INTERVAL_SECONDS * 1000L
private const val MAX_DELAY_MILLIS = BuildConfig.LAUNCHER_WATCHDOG_MAX_INTERVAL_SECONDS * 1000L

private const val MSG_VALIDATE_FOREGROUND_APP = 0
private const val MSG_CANCEL_VALIDATION = 1

class ForegroundAppWatchdogLauncher @Inject constructor(
    private val context: Context,
    private val timeUtil: ITimeUtil,
    private val updaterStateTracker: IUpdaterStateTracker,
    private val schedulers: IThreadSchedulers
) : IForegroundAppWatchdogLauncher {

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    private val uiHandler = Handler(Looper.getMainLooper(), Handler.Callback { msg ->
        when (msg.what) {
            MSG_VALIDATE_FOREGROUND_APP -> actuallyValidate()
            MSG_CANCEL_VALIDATION -> actuallyCancelValidation()
        }
        true
    })

    override fun scheduleForegroundProcessValidation() {
        uiHandler.sendEmptyMessage(MSG_VALIDATE_FOREGROUND_APP)
    }

    override fun cancelPendingAndOnGoingValidation() {
        uiHandler.sendEmptyMessage(MSG_CANCEL_VALIDATION)
    }

    private fun actuallyValidate() {
        Timber.d("[ForegroundAppWatchdog] Schedule next foreground app validation (min: $MIN_DELAY_MILLIS milliseconds, max: $MAX_DELAY_MILLIS milliseconds).")
        schedulers.ensureMainThread()

        val request = PeriodicWorkRequest
            .Builder(ForegroundAppWatchdogWorker::class.java, MIN_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(buildConstraints())
            .build()

        // Add next check time to to heartbeat metadata!
        updaterStateTracker.addStateMetadata(
            mapOf(
                KEY_NEXT_CHECK_TIME to timeUtil.now().plusMillis(MIN_DELAY_MILLIS).atZone(ZoneId.systemDefault()).toString()
            )
        )

        // Mark runnable!
        ForegroundAppWatchdogWorker.canRun.set(true)

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    private fun actuallyCancelValidation() {
        Timber.d("[ForegroundAppWatchdog] Cancel validation.")
        schedulers.ensureMainThread()

        // Cancel any pending work.
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
        // Also stop the running work.
        ForegroundAppWatchdogWorker.canRun.set(false)
    }

    private fun buildConstraints(): Constraints = Constraints.Builder()
        .setTriggerContentMaxDelay(MAX_DELAY_MILLIS, TimeUnit.MILLISECONDS)
        .build()
}
