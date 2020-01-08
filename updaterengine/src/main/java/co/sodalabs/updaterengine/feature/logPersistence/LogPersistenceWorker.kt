package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.di.WorkerInjection
import co.sodalabs.updaterengine.utils.AdbUtils
import co.sodalabs.updaterengine.utils.FileUtils
import io.reactivex.Completable
import io.reactivex.Single
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * The RxWorker for the [androidx.work.WorkManager] that runs every time the
 * log persistence is triggered.
 *
 * It's responsibilities include creation and deletion of log files and delegation
 * of the task of extracting LogCat logs.
 */
class LogPersistenceWorker(
    context: Context,
    params: WorkerParameters
) : RxWorker(context, params) {

    @Inject
    lateinit var appPreference: IAppPreference
    @Inject
    lateinit var adbUtils: AdbUtils
    @Inject
    lateinit var fileUtils: FileUtils
    @Inject
    lateinit var logSender: ILogSender
    @Inject
    lateinit var config: ILogPersistenceConfig
    @Inject
    lateinit var timeUtils: ITimeUtil
    @Inject
    lateinit var logFileProvider: ILogFileProvider
    @Inject
    lateinit var logPersistenceLauncher: ILogsPersistenceLauncher

    private val uiHandler = Handler(Looper.getMainLooper())

    private val logFile: File by lazy { logFileProvider.logFile }
    private val tempLogFile: File by lazy { logFileProvider.tempLogFile }

    // FIXME: Replace the RxWorker with the regular one.
    override fun createWork(): Single<Result> {
        WorkerInjection.inject(this)

        // Log information about next scheduling of this task
        Timber.i("[LogPersistence] Persistence task started at ${timeUtils.systemZonedNow()}")
        val isRepeatTask = inputData.getBoolean(LogsPersistenceConstants.PARAM_REPEAT_TASK, false)
        if (isRepeatTask) {
            val nextTriggerTime = timeUtils.systemZonedNow()
                .plusNanos(TimeUnit.MILLISECONDS.toNanos(config.repeatIntervalInMillis))
            Timber.i("[LogPersistence] Next persistence job scheduled for $nextTriggerTime")
        } else {
            Timber.i("[LogPersistence] No future scheduling, this is a one-time task")
        }
        // Flag indicating whether the user triggered this action via Admin UI
        val isUserTriggered = inputData.getBoolean(LogsPersistenceConstants.PARAM_TRIGGERED_BY_USER, false)
        val workSource = if (isUserTriggered) {
            // If the current operation is due to user triggered event and the log file already
            // exists, then just send the logs to the server. Otherwise, first run the persistence
            // sequence before attempting to send the logs.
            Single
                .fromCallable {
                    logFile.exists()
                }
                .flatMapCompletable { isExists ->
                    if (isExists) {
                        logSender.sendLogsToServer(logFile)
                    } else {
                        persistLogsLocally()
                            .andThen(logSender.sendLogsToServer(logFile))
                    }
                }
        } else {
            persistLogsLocally()
        }

        return workSource
            .doOnComplete {
                Timber.i("[LogPersistence] Persistence task completed at ${timeUtils.systemZonedNow()}")
            }
            .toSingleDefault(Result.success())
            .onErrorReturn { error ->
                when (error) {
                    // Retry for known exceptions.
                    is TimeoutException,
                    is SocketTimeoutException -> Result.retry()
                    // Don't retry for the other cases.
                    else -> Result.failure()
                }
            }
            .doAfterSuccess {
                if (isUserTriggered) {
                    // Note: One-shot work replaces the periodic work to avoid
                    // the race condition of accessing the file. Therefore, we
                    // will need to start the periodic work afterwards.
                    uiHandler.post { logPersistenceLauncher.schedulePeriodicBackingUpLogToCloud() }
                }
            }
    }

    private fun persistLogsLocally(): Completable {
        return checkShouldDeleteFile()
            .flatMap { (file, shouldDelete) -> backupAndDeleteLogsIfRequired(file, shouldDelete) }
            .flatMap { file -> prepareFile(file) }
            .flatMapCompletable { file -> readLogsToFile(file) }
    }

    private fun checkShouldDeleteFile(): Single<Pair<File, Boolean>> {
        return Single
            .fromCallable {
                val createdOn = appPreference.logFileCreatedTimestamp
                // TODO: Might also want to check if we have sufficient storage available on the device
                // Not urgent because we only target one device over which we have complete control
                // Delete file if size or duration limit is crossed
                val isSizeLimitExceeded = fileUtils.isExceedSize(logFile, config.maxLogFileSizeInBytes)
                val isExpired = if (createdOn != LogsPersistenceConstants.INVALID_CREATION_DATE) {
                    fileUtils.isOlderThanDuration(createdOn, config.maxLogFieDurationInMillis)
                } else {
                    false
                }
                val shouldDeleteFile = isSizeLimitExceeded || isExpired
                Timber.i("[LogPersistence] Should Delete File? $shouldDeleteFile")
                Timber.i("[LogPersistence] Size Limit Exceeded: $isSizeLimitExceeded, Expired: $isExpired")
                logFile to shouldDeleteFile
            }
    }

    private fun backupAndDeleteLogsIfRequired(file: File, shouldDelete: Boolean): Single<File> {
        return if (shouldDelete) {
            Timber.i("[LogPersistence] Deleting file")

            // Send logs to the server before deleting them.
            // Even if sending fails, we want to delete the logs because otherwise there is a
            // high chance that we might get stuck into a cycle where the log file grows larger
            // with every iteration and we can't send it due to its size, resulting in the file
            // never getting deleted.
            logSender.sendLogsToServer(file)
                .timeout(Intervals.TIMEOUT_UPLOAD_MIN, TimeUnit.MINUTES)
                .andThen(deleteLogFile(file))
                .flatMap { Single.just(file) }
        } else {
            Single.just(file)
        }
    }

    private fun deleteLogFile(file: File): Single<Boolean> {
        return Single
            .fromCallable { file.delete() }
    }

    private fun prepareFile(file: File): Single<File> {
        return Single
            .create<File> { emitter ->
                try {
                    // Create file and directory if does not exist
                    if (!file.parentFile.exists()) {
                        Timber.i("[LogPersistence] Creating logs directory at ${file.parentFile.path}")
                        file.parentFile.mkdirs()
                    }
                    if (!file.exists()) {
                        Timber.i("[LogPersistence] Creating log file")
                        val success = file.createNewFile()
                        if (success) {
                            // Java IO up to Java 7 (and Android API level 26) does not provide a way
                            // to know the creation date of a file, so we record it manually
                            val time = timeUtils.systemZonedNow()
                            appPreference.logFileCreatedTimestamp = time.toInstant().toEpochMilli()
                            Timber.i("[LogPersistence] Log file created at: $time")
                        } else {
                            throw IllegalStateException("Failed to create temporary file")
                        }
                    }
                    emitter.onSuccess(file)
                } catch (e: IOException) {
                    if (emitter.isDisposed) return@create
                    emitter.onError(e)
                }
            }
    }

    private fun readLogsToFile(file: File): Completable {
        tempLogFile.createNewFile()
        // The ADB commands run in a separate process and take a couple of milliseconds to
        // complete, this timer introduces a delay of 500ms to ensure that we read file after
        // the ADB command is executed
        return Completable.mergeArray(
            adbUtils.copyLogsToFile(file = tempLogFile, maxLineCount = config.maxLogLinesCount, whiteList = config.whitelist),
            Completable.timer(Intervals.DELAY_ADB, TimeUnit.MILLISECONDS))
            .andThen(copyToFile(tempLogFile, file))
    }

    private fun copyToFile(
        temp: File,
        file: File
    ): Completable {
        return Completable
            .create { emitter ->
                try {
                    temp.inputStream().use { input ->
                        FileOutputStream(file, true).use { output ->
                            var bytesCopied: Long = 0
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytes = input.read(buffer)
                            // Notice we only continue if the emitter isn't disposed!
                            while (bytes >= 0 && !emitter.isDisposed) {
                                output.write(buffer, 0, bytes)
                                bytesCopied += bytes
                                bytes = input.read(buffer)
                            }
                        }
                        Timber.i("[LogPersistence] Wrote ${temp.length()} bytes to temp file")
                        temp.delete()
                        emitter.onComplete()

                        emitter.setCancellable {
                            temp.delete()
                        }
                    }
                } catch (e: IOException) {
                    if (emitter.isDisposed) return@create
                    emitter.onError(e)
                }
            }
    }
}