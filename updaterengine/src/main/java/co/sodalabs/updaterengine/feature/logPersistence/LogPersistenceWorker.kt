package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.di.utils.ChildWorkerFactory
import co.sodalabs.updaterengine.utils.AdbUtils
import co.sodalabs.updaterengine.utils.FileUtils
import io.reactivex.Completable
import io.reactivex.Single
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

/**
 * The RxWorker for the [androidx.work.WorkManager] that runs every time the
 * log persistence is triggered.
 *
 * It's responsibilities include creation and deletion of log files and delegation
 * of the task of extracting LogCat logs.
 */
class LogPersistenceWorker(
    private val context: Context,
    private val params: WorkerParameters,
    private val prefs: IAppPreference,
    private val adbUtils: AdbUtils,
    private val fileUtils: FileUtils,
    private val logSender: ILogSender,
    private val config: ILogPersistenceConfig,
    private val logFileProvider: ILogFileProvider
) : RxWorker(context, params) {

    private val logFile: File = logFileProvider.logFile
    private val tempLogFile: File = logFileProvider.tempLogFile

    override fun createWork(): Single<Result> {
        // Flag indicating whether the user triggered this action via Admin UI
        val isUserTriggered =
            params.inputData.getBoolean(LogsPersistenceConstants.PARAM_TRIGGERED_BY_USER, false)
        val workSource = if (isUserTriggered) {
            // If the current operation is due to user triggered event and the log file already
            // exists, then just send the logs to the server. Otherwise, first run the persistence
            // sequence before attempting to send the logs.
            Single
                .fromCallable { logFile.exists() }
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
            .andThen(Single.just(Result.success()))
            .onErrorReturnItem(Result.failure())
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
                val createdOn = prefs.logFileCreatedTimestamp
                // TODO: Might also want to check if we have sufficient storage available on the device
                // Not urgent because we only target one device over which we have complete control
                // Delete file if size or duration limit is crossed
                val isSizeLimitExceeded = fileUtils.isExceedSize(logFile, config.maxLogFileSize)
                val isExpired = if (createdOn != LogsPersistenceConstants.INVALID_CREATION_DATE) {
                    fileUtils.isOlderThanDuration(createdOn, config.maxLogFieDuration)
                } else {
                    false
                }
                val shouldDeleteFile = isSizeLimitExceeded || isExpired
                Timber.i("[LogPersist] Should Delete File? $shouldDeleteFile \nSize Limit Exceeded: $isSizeLimitExceeded, Expired: $isExpired")
                logFile to shouldDeleteFile
            }
    }

    private fun backupAndDeleteLogsIfRequired(file: File, shouldDelete: Boolean): Single<File> {
        return if (shouldDelete) {
            Timber.i("[LogPersist] Deleting file")
            // Send logs to the server before deleting them.
            // Even if sending fails, we want to delete the logs because otherwise there is a
            // high chance that we might get stuck into a cycle where the log file grows larger
            // with every iteration and we can't send it due to its size, resulting in the file
            // never getting deleted.
            logSender.sendLogsToServer(file)
                .timeout(Intervals.TIMEOUT_UPLOAD_MIN, TimeUnit.MINUTES)
                .onErrorComplete()
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
                        Timber.i("[LogPersist] Creating logs directory at ${file.parentFile.path}")
                        file.parentFile.mkdirs()
                    }
                    if (!file.exists()) {
                        Timber.i("[LogPersist] Creating log file")
                        val success = file.createNewFile()
                        if (success) {
                            // Java IO up to Java 7 (and Android API level 26) does not provide a way
                            // to know the creation date of a file, so we record it manually
                            val time = ZonedDateTime.now(ZoneId.systemDefault())
                            prefs.logFileCreatedTimestamp = time.toInstant().toEpochMilli()
                            Timber.i("[LogPersist] Log file created at: $time")
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
            adbUtils.copyLogsToFile(tempLogFile, config.tag),
            Completable.timer(Intervals.DELAY_ADB, TimeUnit.MILLISECONDS))
            .andThen(copyToFile(tempLogFile, file))
    }

    private fun copyToFile(temp: File, file: File): Completable {
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

    class Factory @Inject constructor(
        private val prefs: Provider<IAppPreference>,
        private val adbUtils: Provider<AdbUtils>,
        private val fileUtils: Provider<FileUtils>,
        private val logSender: Provider<ILogSender>,
        private val config: Provider<ILogPersistenceConfig>,
        private val logFileProvider: Provider<ILogFileProvider>
    ) : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): RxWorker {
            return LogPersistenceWorker(
                appContext,
                params,
                prefs.get(),
                adbUtils.get(),
                fileUtils.get(),
                logSender.get(),
                config.get(),
                logFileProvider.get()
            )
        }
    }
}
