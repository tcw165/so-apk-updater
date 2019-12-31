package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.sodalabs.updaterengine.extension.ensureMainThread
import co.sodalabs.updaterengine.feature.logPersistence.LogsPersistenceConstants.ONE_SHOT_WORK_NAME
import co.sodalabs.updaterengine.feature.logPersistence.LogsPersistenceConstants.PERIODIC_WORK_NAME
import co.sodalabs.updaterengine.utils.getWorkInfoByIdObservable
import io.reactivex.Observable
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This component is responsible for scheduling of log persistence task
 *
 * @param context The application context object that used to request a [WorkManager] instance
 */
class LogsPersistenceLauncher @Inject constructor(
    private val context: Context,
    private val persistenceConfig: ILogPersistenceConfig
) : ILogsPersistenceLauncher {

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    override fun scheduleBackingUpLogToCloud() {
        ensureMainThread()

        val requestData = Data.Builder()
            .putBoolean(LogsPersistenceConstants.PARAM_REPEAT_TASK, true)
            .build()
        val requestConstraints = provideCommonConstraint()
        val request = PeriodicWorkRequest
            .Builder(LogPersistenceWorker::class.java, persistenceConfig.repeatIntervalInMillis, TimeUnit.MILLISECONDS)
            .setConstraints(requestConstraints)
            .setInputData(requestData)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request)
    }

    override fun cancelPendingAndRunningBackingUp() {
        ensureMainThread()

        // Cancel all the pending works.
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(ONE_SHOT_WORK_NAME)
    }

    override fun backupLogToCloudNow(): Observable<Boolean> {
        ensureMainThread()

        val requestData = Data.Builder()
            .putBoolean(LogsPersistenceConstants.PARAM_TRIGGERED_BY_USER, true)
            .build()
        val requestConstraints = provideCommonConstraint()
        val request = OneTimeWorkRequest
            .Builder(LogPersistenceWorker::class.java)
            .setConstraints(requestConstraints)
            .setInputData(requestData)
            .build()
        val requestId = request.id
        workManager.enqueueUniqueWork(
            ONE_SHOT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        return workManager.getWorkInfoByIdObservable(requestId)
            .map { it.state }
            .filter { state ->
                state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED
            }
            .map { state -> state == WorkInfo.State.SUCCEEDED }
    }

    private fun provideCommonConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}