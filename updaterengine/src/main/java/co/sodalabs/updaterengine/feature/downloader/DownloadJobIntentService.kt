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
import co.sodalabs.updaterengine.DOWNLOAD_HTTP_CLIENT
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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Named
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
                Timber.v("[Download] (< 21) Schedule a download, using AlarmManager, at $triggerAtMillis milliseconds")

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
                Timber.v("[Download] (>= 21) Schedule a download, using android-21 JobScheduler")

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
                Timber.v("[Download] (< 21) Cancel any pending download, using AlarmManager")

                val intent = Intent(context, DownloadJobIntentService::class.java)
                intent.action = IntentActions.ACTION_DOWNLOAD_APP_UPDATE
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                alarmManager.cancel(pendingIntent)

                // TODO: Cancel firmware update too
            } else {
                Timber.v("[Download] (>= 21) Cancel any pending download, using android-21 JobScheduler")

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
    @field:Named(DOWNLOAD_HTTP_CLIENT)
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
        try {
            val (completedTasks, errors) = downloadBatchUpdates(
                urls = updates.map { it.downloadUrl },
                diskLruCache = updaterConfig.updateDiskCache,
                downloadingCallback = object : IDownloadListener {
                    override fun onDownloading(
                        urlIndex: Int,
                        progressPercentage: Int,
                        currentBytes: Long,
                        totalBytes: Long
                    ) {
                        // Let the engine know the download is in-progress.
                        UpdaterService.notifyAppUpdateDownloadProgress(
                            context = this@DownloadJobIntentService,
                            update = updates[urlIndex],
                            percentageComplete = progressPercentage,
                            currentBytes = currentBytes,
                            totalBytes = totalBytes
                        )
                    }
                }
            )

            // Let the engine know the download finishes.
            UpdaterService.notifyAppUpdateDownloaded(
                context = this,
                foundUpdates = updates,
                downloadedUpdates = completedTasks.toDownloadedAppUpdate(updates),
                errors = errors
            )
        } catch (interrupt: DownloadCancelledException) {
            // Only log and don't do any further transition!
            Timber.w(interrupt)
        }
    }

    private fun List<CompletedTask>.toDownloadedAppUpdate(
        updates: List<AppUpdate>
    ): List<DownloadedAppUpdate> {
        val downloadedUpdates = mutableListOf<DownloadedAppUpdate>()
        for (task in this) {
            downloadedUpdates.add(
                DownloadedAppUpdate(
                    file = task.downloadedFile,
                    fromUpdate = updates[task.updateIndex]
                )
            )
        }
        return downloadedUpdates
    }

    // Download For Firmware //////////////////////////////////////////////////

    private fun downloadFirmwareUpdate(
        updates: List<FirmwareUpdate>
    ) {
        require(updates.size == 1) { "There should be one firmware found at a time" }
        val theSoleUpdate = updates.first()

        try {
            // Execute batch download
            val (completedTasks, errors) = downloadBatchUpdates(
                urls = updates.map { it.fileURL },
                diskLruCache = updaterConfig.updateDiskCache,
                downloadingCallback = object : IDownloadListener {
                    override fun onDownloading(
                        urlIndex: Int,
                        progressPercentage: Int,
                        currentBytes: Long,
                        totalBytes: Long
                    ) {
                        // Let the engine know the download is in-progress.
                        UpdaterService.notifyFirmwareUpdateDownloadProgress(
                            context = this@DownloadJobIntentService,
                            update = updates[urlIndex],
                            percentageComplete = progressPercentage,
                            currentBytes = currentBytes,
                            totalBytes = totalBytes
                        )
                    }
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
        } catch (interrupt: DownloadCancelledException) {
            // Only log and don't do any further transition!
            Timber.w(interrupt)
        }
    }

    // Common /////////////////////////////////////////////////////////////////

    @Throws(DownloadCancelledException::class)
    private fun downloadBatchUpdates(
        urls: List<String>,
        diskLruCache: DiskLruCache,
        downloadingCallback: IDownloadListener
    ): Pair<List<CompletedTask>, List<Throwable>> {
        Timber.v("[Download] Start downloading ${urls.size} updates")

        val completedTasks = mutableListOf<CompletedTask>()
        val errors = mutableListOf<Throwable>()

        // Delete the cache before downloading if we don't use cache.
        if (!updaterConfig.downloadUseCache) {
            Timber.v("[Download] Delete the cache cause 'download using cache' is disabled!")
            diskLruCache.delete()
        }
        // Open the cache
        if (!diskLruCache.isOpened()) {
            Timber.v("[Download] Open cache!")
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
            val totalBytes = try {
                requestFileSize(url)
            } catch (error: Throwable) {
                Timber.w(error)
                // We'll collect the error and continue.
                errors.add(error)
                continue
            }

            // Step 2, download the file if cache file size is smaller than the total size.
            val cacheEditor = diskLruCache.edit(urlFileName)
            val cacheFile = cacheEditor.file
            try {
                // Ensure the file presence!
                cacheFile.createNewFile()

                val cacheFileSize = cacheFile.length()
                Timber.v("[Download] Open the cache '$cacheFile', $cacheFileSize bytes.")

                // Used to prevent progress updates of the same value
                var currentBytes = cacheFileSize
                var workingPercentage = computePercentage(currentBytes, totalBytes)

                // Log percentage before the download starts.
                Timber.v("[Download] Download '$url'... $workingPercentage%")

                while (canRun.get() && currentBytes < totalBytes) {
                    currentBytes = downloadNextChunk(url, cacheFile, totalBytes)

                    val percentage = computePercentage(currentBytes, totalBytes)
                    Timber.v("[Download] Download '$url'... $percentage%")

                    // FIXME: The LRU cache library has a problem in the CLEAN
                    // FIXME: and DIRTY annotation in the journal file.
                    // FIXME: That design is to control the bytes within the
                    // FIXME: capacity; If the record is marked as DIRTY, it
                    // FIXME: would be removed on the cache initialization.
                    // FIXME: That means if you power off the device while it's
                    // FIXME: downloading a file. The file would be deleted next
                    // FIXME: on-boot cause the file is marked DIRTY!

                    // Prevent duplicate percentage notifications
                    if (workingPercentage != percentage) {
                        workingPercentage = percentage

                        downloadingCallback.onDownloading(
                            i,
                            workingPercentage,
                            currentBytes,
                            totalBytes
                        )
                    }
                }

                if (currentBytes > totalBytes) {
                    // Throw exception as the cache size is greater than the expected size.
                    throw DownloadInvalidFileSizeException(cacheFile, currentBytes, totalBytes)
                }

                when {
                    currentBytes == totalBytes -> {
                        Timber.v("[Download] Download '$url'... 100%")
                        completedTasks.add(CompletedTask(i, cacheFile))
                    }
                    canRun.get() -> {
                        // If the size is not matched and it's still allowed running,
                        // throw the invalid size exception.
                        throw DownloadInvalidFileSizeException(cacheFile, currentBytes, totalBytes)
                    }
                    else -> {
                        // Otherwise, it's a cancellation.
                        throw DownloadCancelledException(url)
                    }
                }
            } catch (error: Throwable) {
                // Conditionally collect the error and continue.
                when (error) {
                    is DownloadCancelledException -> {
                        // Populate the cancel exception to the caller to make
                        // decision.
                        throw error
                    }
                    else -> {
                        Timber.w(error)
                        errors.add(error)
                    }
                }
            } finally {
                Timber.v("[Download] Close the cache '$cacheFile'")
                try {
                    cacheEditor.commit()
                } catch (ignored: Throwable) {
                    // No-op
                }
            }
        }

        // Complete
        return Pair(completedTasks, errors)
    }

    private fun requestFileSize(
        url: String
    ): Long {
        Timber.v("[Download] request file size for '$url'...")
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

        Timber.v("[Download] request file size for '$url'... $length bytes")
        return length
    }

    private fun downloadNextChunk(
        url: String,
        cacheFile: File,
        totalBytes: Long
    ): Long {
        val currentBytes = cacheFile.length()
        return if (currentBytes < totalBytes) {

            val dateString = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val buff = ByteArray(BUFFER_IN_BYTES)
            var newCurrentBytes = currentBytes

            // Download the file chunk by chunk (progressive download)
            val endSize = min(newCurrentBytes + CHUNK_IN_BYTES, totalBytes)
            val headers = hashMapOf(
                Pair("Range", "bytes=$newCurrentBytes-$endSize"),
                Pair("x-ms-range", "bytes=$newCurrentBytes-$endSize"),
                Pair("x-ms-date", dateString),
                Pair("Date", dateString)
            )
            val request = Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()

            if (!canRun.get()) return newCurrentBytes

            if (response.isSuccessful) {
                val body = response.body()
                val bodyInputStream = body?.byteStream()

                bodyInputStream.use { inputStream ->
                    FileOutputStream(cacheFile, true).use { outputStream ->
                        // Write the response to the local file piece by piece.
                        while (canRun.get()) {
                            val read = inputStream?.read(buff) ?: -1
                            if (read <= 0) break

                            outputStream.write(buff, 0, read)
                            newCurrentBytes += read
                        }
                        outputStream.flush()
                    }
                }
            } else {
                throw DownloadUnknownErrorException(response.code(), url)
            }

            newCurrentBytes
        } else {
            currentBytes
        }
    }

    private fun computePercentage(
        currentBytes: Long,
        totalBytes: Long
    ): Int {
        return (100f * currentBytes / totalBytes).roundToInt()
    }

    private data class CompletedTask(
        val updateIndex: Int,
        val downloadedFile: File
    )
}