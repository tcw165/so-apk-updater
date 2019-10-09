package co.sodalabs.updaterengine.feature.downloader

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable
import android.os.PersistableBundle
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedAppUpdate
import co.sodalabs.updaterengine.data.DownloadedFirmwareUpdate
import co.sodalabs.updaterengine.data.FirmwareUpdate
import co.sodalabs.updaterengine.exception.DownloadCancelledException
import co.sodalabs.updaterengine.exception.DownloadInvalidFileSizeException
import co.sodalabs.updaterengine.exception.DownloadSizeNotFoundException
import co.sodalabs.updaterengine.exception.DownloadUnknownErrorException
import co.sodalabs.updaterengine.exception.HttpMalformedURIException
import co.sodalabs.updaterengine.feature.installer.InstallerJobService
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import dagger.android.AndroidInjection
import io.reactivex.disposables.CompositeDisposable
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.min
import kotlin.math.roundToInt

private const val INVALID_CONTENT_LENGTH_STRING = "-1"

private const val CHUNK_IN_BYTES = 3 * 1024 * 1024
private const val BUFFER_IN_BYTES = 4 * 1024

class DownloadJobIntentService : JobIntentService() {

    companion object {

        fun downloadAppUpdateNow(
            context: Context,
            updates: List<AppUpdate>
        ) {
            downloadUpdateNow(context, updates, IntentActions.ACTION_DOWNLOAD_APP_UPDATE)
        }

        fun downloadFirmwareUpdateNow(
            context: Context,
            update: FirmwareUpdate
        ) {
            // Note: We turn the singular update to a list to be compatible with the batch download.
            downloadUpdateNow(context, listOf(update), IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE)
        }

        private fun <T : Parcelable> downloadUpdateNow(
            context: Context,
            updates: List<T>,
            intentAction: String
        ) {
            val intent = Intent(context, DownloadJobIntentService::class.java)
            intent.action = intentAction
            intent.putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))

            canRun.set(true)
            enqueueWork(context, ComponentName(context, DownloadJobIntentService::class.java), UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES, intent)
        }

        fun scheduleDownloadAppUpdate(
            context: Context,
            updates: List<AppUpdate>,
            triggerAtMillis: Long
        ) {
            scheduleDownloadUpdate(context, updates, triggerAtMillis, IntentActions.ACTION_DOWNLOAD_APP_UPDATE)
        }

        fun scheduleDownloadFirmwareUpdate(
            context: Context,
            update: FirmwareUpdate,
            triggerAtMillis: Long
        ) {
            // Note: We turn the singular update to a list to be compatible with the batch download.
            scheduleDownloadUpdate(context, listOf(update), triggerAtMillis, IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE)
        }

        private fun <T : Parcelable> scheduleDownloadUpdate(
            context: Context,
            updates: List<T>,
            triggerAtMillis: Long,
            intentAction: String
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Install] (< 21) Schedule a download, using AlarmManager, at $triggerAtMillis milliseconds")

                val intent = Intent(context, DownloadJobIntentService::class.java)
                intent.action = intentAction
                intent.putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                Timber.v("[Install] (>= 21) Schedule a download, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, InstallerJobService::class.java)
                val persistentBundle = PersistableBundle()
                persistentBundle.putString(UpdaterJobs.JOB_ACTION, intentAction)
                // FIXME: How to persist the data? As JSON String, we also need
                // FIXME: to take care of the AlarmManager case.
                // persistentBundle.put(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))

                val builder = JobInfo.Builder(UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES, componentName)
                    .setRequiresDeviceIdle(false)
                    .setExtras(persistentBundle)

                if (Build.VERSION.SDK_INT >= 26) {
                    builder.setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                }

                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

                // Note: The job would be consumed by CheckJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES)
                jobScheduler.schedule(builder.build())
            }
        }

        fun cancelDownload(
            context: Context
        ) {
            // Stop any running task.
            canRun.set(false)

            // Kill the pending task.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Install] (< 21) Cancel any pending download, using AlarmManager")

                val intent = Intent(context, DownloadJobIntentService::class.java)
                intent.action = IntentActions.ACTION_DOWNLOAD_APP_UPDATE
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                alarmManager.cancel(pendingIntent)

                // TODO: Cancel firmware update too
            } else {
                Timber.v("[Install] (>= 21) Cancel any pending download, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                // Note: The job would be consumed by InstallerJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES)

                // TODO: Cancel firmware update too
            }
        }

        @JvmStatic
        private val canRun = AtomicBoolean(true)
    }

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        Timber.v("[Download] Downloader Service is online")
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.v("[Download] Downloader Service is offline")
        disposables.clear()
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_DOWNLOAD_APP_UPDATE -> {
                val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
                downloadAppUpdate(updates.toList())
            }
            IntentActions.ACTION_DOWNLOAD_FIRMWARE_UPDATE -> {
                val updates = intent.getParcelableArrayListExtra<FirmwareUpdate>(IntentActions.PROP_FOUND_UPDATES)
                require(updates.size == 1) { "For firmware update, there should be only one found update!" }
                downloadFirmwareUpdate(updates)
            }
            else -> throw IllegalArgumentException("Hey develop, DownloadJobIntentService is for downloading the updates only!")
        }
    }

    // Download For App ///////////////////////////////////////////////////////

    private fun downloadAppUpdate(
        updates: List<AppUpdate>
    ) {
        // Execute batch download
        val (completedTasks, errors) = downloadBatchUpdate(
            urls = updates.map { it.downloadUrl },
            diskLruCache = updaterConfig.apkDiskCache,
            downloadingCallback = { urlIndex: Int, progressPercentage: Int, currentBytes: Long, totalBytes: Long ->
                // Let the engine know the download is in-progress.
                UpdaterService.notifyAppUpdateDownloadProgress(
                    context = this,
                    update = updates[urlIndex],
                    percentageComplete = progressPercentage,
                    currentBytes = currentBytes,
                    totalBytes = totalBytes
                )
            }
        )

        // Let the engine know the download finishes.
        UpdaterService.notifyAppUpdateDownloaded(
            context = this,
            foundUpdates = updates,
            downloadedUpdates = completedTasks.toDownloadedAppUpdate(updates),
            errors = errors
        )
    }

    private fun List<CompletedTask>.toDownloadedAppUpdate(
        updates: List<AppUpdate>
    ): List<DownloadedAppUpdate> {
        val downloadedUpdates = mutableListOf<DownloadedAppUpdate>()
        for (task in this) {
            downloadedUpdates.add(DownloadedAppUpdate(
                file = task.downloadedFile,
                fromUpdate = updates[task.updateIndex]
            ))
        }
        return downloadedUpdates
    }

    // Download For Firmware //////////////////////////////////////////////////

    private fun downloadFirmwareUpdate(
        updates: List<FirmwareUpdate>
    ) {
        require(updates.size == 1) { "There should be one firmware found at a time" }
        val theSoleUpdate = updates.first()

        // Execute batch download
        val (completedTasks, errors) = downloadBatchUpdate(
            urls = updates.map { it.fileURL },
            diskLruCache = updaterConfig.firmwareDiskCache,
            downloadingCallback = { urlIndex: Int, progressPercentage: Int, currentBytes: Long, totalBytes: Long ->
                // Let the engine know the download is in-progress.
                UpdaterService.notifyFirmwareUpdateDownloadProgress(
                    context = this,
                    update = updates[urlIndex],
                    percentageComplete = progressPercentage,
                    currentBytes = currentBytes,
                    totalBytes = totalBytes
                )
            }
        )

        if (completedTasks.isNotEmpty()) {
            require(completedTasks.size == 1) { "There should be one firmware download task at a time" }

            val theSoleTask = completedTasks.first()
            val file = theSoleTask.downloadedFile

            // Let the engine know the download finishes.
            UpdaterService.notifyFirmwareUpdateDownloadComplete(
                context = this,
                foundUpdate = theSoleUpdate,
                downloadedUpdate = DownloadedFirmwareUpdate(file, theSoleUpdate)
            )
        } else {
            require(errors.size == 1) { "There should be an error" }
            val theSoleError = errors.first()

            // Let the engine know the download fails.
            UpdaterService.notifyFirmwareUpdateDownloadError(
                context = this,
                foundUpdate = theSoleUpdate,
                error = theSoleError
            )
        }
    }

    // Common /////////////////////////////////////////////////////////////////

    private fun downloadBatchUpdate(
        urls: List<String>,
        diskLruCache: DiskLruCache,
        downloadingCallback: (urlIndex: Int, progressPercentage: Int, currentBytes: Long, totalBytes: Long) -> Unit
    ): Pair<List<CompletedTask>, List<Throwable>> {
        Timber.v("[Download] Start downloading ${urls.size} updates")

        val completedTasks = mutableListOf<CompletedTask>()
        val errors = mutableListOf<Throwable>()

        // Delete the cache before downloading if we don't use cache.
        if (!updaterConfig.downloadUseCache) {
            diskLruCache.delete()
        }
        // Open the cache
        if (!diskLruCache.isOpened) {
            diskLruCache.open()
        }

        for (i in 0 until urls.size) {
            val url = urls[i]
            val urlFileName = try {
                Uri.parse(url).lastPathSegment
                    ?: throw HttpMalformedURIException(url)
            } catch (error: Throwable) {
                errors.add(error)
                continue
            }

            // Step 1, send a HEAD request for the file size
            val totalSize = try {
                requestFileSize(url)
            } catch (error: Throwable) {
                Timber.e(error)
                // We'll collect the error and continue.
                errors.add(error)
                continue
            }

            // Step 2, download the file if cache file size is smaller than the total size.
            val cacheEditor = diskLruCache.edit(urlFileName)
            val cacheFile = cacheEditor.getFile(0)
            Timber.v("[Download] Open the cache \"$cacheFile\"")
            try {
                executeDownload(i, url, totalSize, cacheFile, downloadingCallback)
                completedTasks.add(CompletedTask(i, cacheFile))
            } catch (error: Throwable) {
                Timber.e(error)
                // We'll collect the error and continue.
                errors.add(error)
            } finally {
                Timber.v("[Download] Close the cache \"$cacheFile\"")
                cacheEditor.commit()
            }
        }

        // Complete
        return Pair(completedTasks, errors)
    }

    private fun requestFileSize(
        url: String
    ): Long {
        Timber.v("[Download] request file size for \"$url\"...")
        val dateString = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val headers = hashMapOf(
            Pair("x-ms-date", dateString),
            Pair("Date", dateString)
        )
        val headRequest = Request.Builder()
            .url(url)
            .headers(Headers.of(headers))
            // A HEAD request for retrieving the size.
            .head()
            .build()
        val headResponse = okHttpClient.newCall(headRequest).execute()
        val lengthString = headResponse.header("Content-Length", INVALID_CONTENT_LENGTH_STRING)
            ?: throw DownloadSizeNotFoundException(url)
        val length = lengthString.toLong()

        Timber.v("[Download] request file size for \"$url\"... $length bytes")
        return length
    }

    private fun executeDownload(
        taskIndex: Int,
        url: String,
        totalSize: Long,
        cacheFile: File,
        onDownloadingCallback: (urlIndex: Int, progressPercentage: Int, currentBytes: Long, totalBytes: Long) -> Unit
    ) {
        cacheFile.createNewFile()
        val cacheFileSize = cacheFile.length()

        // Used to prevent progress updates of the same value
        var workingPercentage = -1

        if (cacheFileSize < totalSize) {
            val startPercentage = (100f * cacheFileSize / totalSize).roundToInt()
            Timber.v("[Download] Download \"$url\"... $startPercentage%")
            val dateString = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val buff = ByteArray(BUFFER_IN_BYTES)
            var currentSize = cacheFileSize

            // Download the file chunk by chunk (progressive download)
            while (canRun.get() && currentSize < totalSize) {
                val endSize = min(currentSize + CHUNK_IN_BYTES, totalSize)
                val headers = hashMapOf(
                    Pair("Range", "bytes=$currentSize-$endSize"),
                    Pair("x-ms-range", "bytes=$currentSize-$endSize"),
                    Pair("x-ms-date", dateString),
                    Pair("Date", dateString)
                )
                val request = Request.Builder()
                    .url(url)
                    .headers(Headers.of(headers))
                    .get()
                    .build()
                val response = okHttpClient.newCall(request).execute()

                if (canRun.get() && response.isSuccessful) {
                    var inputStream: InputStream? = null
                    val outputStream = FileOutputStream(cacheFile, true)
                    try {
                        inputStream = response.body()?.byteStream()

                        // Write the response to the local file piece by piece.
                        while (canRun.get()) {
                            val read = inputStream?.read(buff) ?: -1
                            if (read <= 0) break

                            outputStream.write(buff, 0, read)
                            currentSize += read

                            // TODO: Timeout management?
                        }
                    } finally {
                        inputStream?.apply { close() }
                        outputStream.flush()
                        outputStream.close()
                    }
                } else {
                    throw DownloadUnknownErrorException(response.code(), url)
                }

                val percentage = (100f * currentSize / totalSize).roundToInt()
                Timber.v("[Download] Download \"$url\"... $percentage%")

                // Prevent duplicate percentage notifications
                if (workingPercentage != percentage) {
                    workingPercentage = percentage

                    onDownloadingCallback.invoke(
                        taskIndex,
                        workingPercentage,
                        currentSize,
                        totalSize
                    )
                }
            }

            // TODO: Timeout management?

            if (currentSize == totalSize) {
                Timber.v("[Download] Download \"$url\"... 100%")
            } else {
                if (canRun.get()) {
                    // If the size is not matched and it's still allowed running,
                    // throw the invalid size exception.
                    throw DownloadInvalidFileSizeException(cacheFile, currentSize, totalSize)
                } else {
                    // Otherwise, it's a cancellation.
                    throw DownloadCancelledException(url)
                }
            }
        } else if (cacheFileSize == totalSize) {
            Timber.v("[Download] Download \"$url\"... 100%")
        } else {
            // Throw exception as the cache size is greater than the expected size.
            throw DownloadInvalidFileSizeException(cacheFile, cacheFileSize, totalSize)
        }
    }

    private data class CompletedTask(
        val updateIndex: Int,
        val downloadedFile: File
    )
}