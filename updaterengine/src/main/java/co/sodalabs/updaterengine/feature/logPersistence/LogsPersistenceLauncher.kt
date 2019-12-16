package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.sodalabs.updaterengine.extension.ensureMainThread
import co.sodalabs.updaterengine.utils.BuildUtils
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

        val data = Data.Builder()
            .putBoolean(LogsPersistenceConstants.PARAM_REPEAT_TASK, true)
            .build()

        if (BuildUtils.isDebug()) {
            val request = OneTimeWorkRequest
                .Builder(LogPersistenceWorker::class.java)
                .addTag(LogsPersistenceConstants.WORK_TAG)
                .build()
            workManager.enqueueUniqueWork(
                LogsPersistenceConstants.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request)
        } else {
            val request = PeriodicWorkRequest
                .Builder(LogPersistenceWorker::class.java, persistenceConfig.repeatIntervalInMillis, TimeUnit.MILLISECONDS)
                .addTag(LogsPersistenceConstants.WORK_TAG)
                .setInputData(data)
                .build()
            workManager.enqueueUniquePeriodicWork(
                LogsPersistenceConstants.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request)
        }
    }

    override fun cancelPendingAndRunningBackingUp() {
        ensureMainThread()

        workManager.cancelAllWorkByTag(LogsPersistenceConstants.WORK_TAG)
    }

    override fun backupLogToCloudNow(): Observable<Boolean> {
        ensureMainThread()

        val data = Data.Builder()
            .putBoolean(LogsPersistenceConstants.PARAM_TRIGGERED_BY_USER, true)
            .build()
        val request = OneTimeWorkRequest
            .Builder(LogPersistenceWorker::class.java)
            .setInputData(data)
            .build()
        val requestId = request.id
        workManager.enqueue(request)
        return workManager.getWorkInfoByIdObservable(requestId)
            .map { it.state }
            .filter { state ->
                state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED
            }
            .map { state -> state == WorkInfo.State.SUCCEEDED }
    }
}