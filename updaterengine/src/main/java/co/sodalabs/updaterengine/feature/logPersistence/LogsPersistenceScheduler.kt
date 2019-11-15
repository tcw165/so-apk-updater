package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import co.sodalabs.updaterengine.di.utils.WorkerFactory
import co.sodalabs.updaterengine.utils.BuildUtils
import co.sodalabs.updaterengine.utils.StorageUtils
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This component is responsible for scheduling of log persistence task
 *
 * @param context The application context object that used to request a [WorkManager] instance
 */
class LogsPersistenceScheduler @Inject constructor(
    private val context: Context,
    private val workerFactory: WorkerFactory,
    private val persistenceConfig: ILogPersistenceConfig
) : ILogsPersistenceScheduler {

    private val workManager by lazy {
        WorkManager.getInstance(context)
    }

    private val logFile: File by lazy {
        val dir = File(StorageUtils.getCacheDirectory(context, false), LogsPersistenceConstants.LOG_DIR)
        File(dir, LogsPersistenceConstants.LOG_FILE)
    }

    override fun start() {
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(context, config)
        val filePath = try {
            logFile.absolutePath
        } catch (e: Exception) {
            // We want to log this to Bugsnag because this is a very unlikely yet an unrecoverable
            // situation which we want to be notified about.
            Timber.e("Invalid log file provided, disabling log persistence. $e")
            stop()
            return
        }
        val data = Data.Builder()
            .putString(LogsPersistenceConstants.PARAM_LOG_FILE, filePath)
            .build()
        if (BuildUtils.isDebug()) {
            val request = OneTimeWorkRequest
                .Builder(LogPersistenceWorker::class.java)
                .setInputData(data)
                .addTag(LogsPersistenceConstants.WORK_TAG)
                .build()
            workManager.enqueueUniqueWork(
                LogsPersistenceConstants.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request)
        } else {
            val request = PeriodicWorkRequest
                .Builder(LogPersistenceWorker::class.java, persistenceConfig.repeatInterval, TimeUnit.HOURS)
                .setInputData(data)
                .addTag(LogsPersistenceConstants.WORK_TAG)
                .build()
            workManager.enqueueUniquePeriodicWork(
                LogsPersistenceConstants.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request)
        }
    }

    override fun stop() {
        workManager.cancelAllWorkByTag(LogsPersistenceConstants.WORK_TAG)
    }
}