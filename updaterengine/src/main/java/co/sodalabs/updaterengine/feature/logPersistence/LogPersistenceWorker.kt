package co.sodalabs.updaterengine.feature.logPersistence

import android.content.Context
import androidx.work.RxWorker
import androidx.work.WorkerParameters
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.di.utils.ChildWorkerFactory
import co.sodalabs.updaterengine.utils.AdbUtils
import co.sodalabs.updaterengine.utils.FileUtils
import co.sodalabs.updaterengine.utils.StorageUtils
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
    private val config: ILogPersistenceConfig
) : RxWorker(context, params) {

    private val tempLogFile by lazy {
        File(StorageUtils.getCacheDirectory(context, false), LogsPersistenceConstants.TEMP_LOG_BUFFER_FILE)
    }

    override fun createWork(): Single<Result> {
        return checkShouldDeleteFile()
            .flatMap { (file, shouldDelete) -> backupAndDeleteLogsIfRequired(file, shouldDelete) }
            .flatMap { file -> prepareFile(file) }
            .flatMapCompletable { file -> readLogsToFile(file) }
            .andThen(Single.just(Result.success()))
            .onErrorReturnItem(Result.failure())
    }

    private fun checkShouldDeleteFile(): Single<Pair<File, Boolean>> {
        return Single
            .fromCallable {
                val createdOn = prefs.logFileCreatedTimestamp
                // TODO: Might also want to check if we have sufficient storage available on the deivce
                // Not urgent because we only target one device over which we have complete control
                val file = File(params.inputData.getString(LogsPersistenceConstants.PARAM_LOG_FILE))
                // Delete file if size or duration limit is crossed
                val isSizeLimitExceeded = fileUtils.isExceedSize(file, config.maxLogFileSize)
                val isExpired = if (createdOn != LogsPersistenceConstants.INVALID_CREATION_DATE) {
                    fileUtils.isOlderThanDuration(createdOn, config.maxLogFieDuration)
                } else {
                    false
                }
                val shouldDeleteFile = isSizeLimitExceeded || isExpired
                Timber.i("[LogPersist] Should Delete File? $shouldDeleteFile \nSize Limit Exceeded: $isSizeLimitExceeded, Expired: $isExpired")
                file to shouldDeleteFile
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
                        Timber.i("[LogPersist] Creating parent directory for logs at ${file.parentFile.path}")
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
                temp.inputStream().use { input ->
                    FileOutputStream(file, true).use { output ->
                        var bytesCopied: Long = 0
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = input.read(buffer)
                        while (bytes >= 0 && !emitter.isDisposed) { // Notice we only continue if the emitter isn't disposed!
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            bytes = input.read(buffer)
                        }
                    }
                    temp.delete()
                }
            }
    }

    class Factory @Inject constructor(
        private val prefs: Provider<IAppPreference>,
        private val adbUtils: Provider<AdbUtils>,
        private val fileUtils: Provider<FileUtils>,
        private val logSender: Provider<ILogSender>,
        private val config: Provider<ILogPersistenceConfig>
    ) : ChildWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): RxWorker {
            return LogPersistenceWorker(
                appContext,
                params,
                prefs.get(),
                adbUtils.get(),
                fileUtils.get(),
                logSender.get(),
                config.get()
            )
        }
    }
}
