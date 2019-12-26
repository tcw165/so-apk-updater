package co.sodalabs.apkupdater.feature.homeCorrector

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import co.sodalabs.updaterengine.extension.ensureMainThread
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val UNIQUE_WORK_NAME_CORRECT_HOME_ACTIVITY = "correct_home_activity"
private const val UNIQUE_WORK_NAME_LAUNCH_SODALABS_HOME_ACTIVITY = "launch_sodalabs_home_activity"

private const val MIN_DELAY_MILLIS = 5L * 1000L
private const val MAX_DELAY_MILLIS = 10L * 1000L

class HomeActivityCorrectorLauncher @Inject constructor(
    private val context: Context
) : IHomeCorrectorLauncher {

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    override fun scheduleStartingSodaLabsLauncher(
        delayMillis: Long
    ) {
        ensureMainThread()

        Timber.v("[HomeActivityCorrector] Schedule work for launching the SodaLabs HOME Activity...")

        val requestConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(Math.min(delayMillis, MAX_DELAY_MILLIS), TimeUnit.MILLISECONDS)
            .build()
        val request = OneTimeWorkRequest
            .Builder(HomeActivityLauncherWorker::class.java)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(requestConstraints)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME_LAUNCH_SODALABS_HOME_ACTIVITY,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun correctDefaultHomeNow() {
        ensureMainThread()

        Timber.v("[HomeActivityCorrector] Schedule work for correcting the HOME Activity...")

        val requestConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(MAX_DELAY_MILLIS, TimeUnit.MILLISECONDS)
            .build()
        val request = OneTimeWorkRequest
            .Builder(HomeActivityCorrectorWorker::class.java)
            .setConstraints(requestConstraints)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME_CORRECT_HOME_ACTIVITY,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}