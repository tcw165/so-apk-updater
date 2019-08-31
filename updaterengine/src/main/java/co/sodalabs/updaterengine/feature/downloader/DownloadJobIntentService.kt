package co.sodalabs.updaterengine.feature.downloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.DownloadCancelledException
import co.sodalabs.updaterengine.exception.DownloadInvalidFileSizeException
import co.sodalabs.updaterengine.exception.DownloadSizeNotFoundException
import co.sodalabs.updaterengine.exception.DownloadUnknownErrorException
import co.sodalabs.updaterengine.exception.HttpMalformedURIException
import co.sodalabs.updaterengine.feature.core.AppUpdaterService
import com.squareup.moshi.Types
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

private const val CHUNK_IN_BYTES = 3 * 1024 * 1024
private const val BUFFER_IN_BYTES = 4 * 1024

class DownloadJobIntentService : JobIntentService() {

    companion object {

        fun downloadNow(
            context: Context,
            updates: List<AppUpdate>
        ) {
            val intent = Intent(context, DownloadJobIntentService::class.java)
            intent.action = IntentActions.ACTION_DOWNLOAD_UPDATES
            intent.putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))

            canRun.set(true)
            enqueueWork(context, ComponentName(context, DownloadJobIntentService::class.java), UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES, intent)
        }

        fun cancelDownload() {
            Timber.v("[Download] Cancel downloading!")
            canRun.set(false)
        }

        @JvmStatic
        private val canRun = AtomicBoolean(true)
    }

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
            IntentActions.ACTION_DOWNLOAD_UPDATES -> {
                val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
                download(updates.toList())
            }
            else -> throw IllegalArgumentException("Hey develop, DownloadJobIntentService is for downloading the updates only!")
        }
    }

    // Download ///////////////////////////////////////////////////////////////

    private fun download(
        updates: List<AppUpdate>
    ) {
        Timber.v("[Download] Start downloading ${updates.size} updates")

        val downloadedUpdates = mutableListOf<DownloadedUpdate>()
        val errors = mutableListOf<Throwable>()

        // Delete the cache before downloading if we don't use cache.
        val apkDiskCache = ApkUpdater.apkDiskCache()
        if (!ApkUpdater.downloadUseCache()) {
            apkDiskCache.delete()
        }
        // Open the cache
        if (!apkDiskCache.isOpened) {
            apkDiskCache.open()
        }

        for (i in 0 until updates.size) {
            val update = updates[i]
            val url = update.downloadUrl
            val urlFileName = Uri.parse(url).lastPathSegment
                ?: throw HttpMalformedURIException(url)

            // Step 1, send a HEAD request for the file size
            val totalSize = try {
                requestFileSize(url)
            } catch (error: Throwable) {
                Timber.e(error)
                errors.add(error)
                continue
            }

            // Step 2, download the file if cache file size is smaller than the total size.
            val cache = ApkUpdater.apkDiskCache()
            val cacheEditor = cache.edit(urlFileName)
            val cacheFile = cacheEditor.getFile(0)
            Timber.v("[Download] Open the cache \"$cacheFile\"")
            try {
                val downloadedUpdate = requestDownload(url, totalSize, update, cacheFile)
                downloadedUpdates.add(downloadedUpdate)
            } catch (error: Throwable) {
                Timber.e(error)
                errors.add(error)
            } finally {
                Timber.v("[Download] Close the cache \"$cacheFile\"")
                cacheEditor.commit()
            }
        }

        persistDownloadedUpdates(downloadedUpdates)

        AppUpdaterService.notifyDownloadsComplete(this, downloadedUpdates, errors)
    }

    private fun requestFileSize(
        url: String
    ): Long {
        Timber.v("[Download] request file size for \"$url\"...")
        val dateString = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        val headers = hashMapOf(
            Pair("x-ms-version", "2018-03-28"), // TODO: Ask Kevin what's this
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
        val lengthString = headResponse.header("Content-Length", "-1")
            ?: throw DownloadSizeNotFoundException(url)
        val length = lengthString.toLong()

        Timber.v("[Download] request file size for \"$url\"... $length bytes")
        return length
    }

    private fun requestDownload(
        url: String,
        totalSize: Long,
        fromUpdate: AppUpdate,
        cacheFile: File
    ): DownloadedUpdate {
        cacheFile.createNewFile()
        val cacheFileSize = cacheFile.length()

        return if (cacheFileSize < totalSize) {
            val startPercentage = Math.round(100f * cacheFileSize / totalSize)
            Timber.v("[Download] Download \"$url\"... $startPercentage%")
            val dateString = Instant.now()
                .atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val buff = ByteArray(BUFFER_IN_BYTES)
            var currentSize = cacheFileSize

            // Download the file chunk by chunk
            while (canRun.get() && currentSize < totalSize) {
                val endSize = if (currentSize + CHUNK_IN_BYTES < totalSize) {
                    currentSize + CHUNK_IN_BYTES
                } else {
                    totalSize
                }
                val headers = hashMapOf(
                    Pair("Range", "bytes=$currentSize-$endSize"),
                    Pair("x-ms-range", "bytes=$currentSize-$endSize"),
                    Pair("x-ms-version", "2018-03-28"), // TODO: Ask Kevin what's this
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

                        // Progressively download
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

                val percentage = Math.round(100f * currentSize / totalSize)
                Timber.v("[Download] Download \"$url\"... $percentage%")
            }

            // TODO: Timeout management?

            if (currentSize == totalSize) {
                DownloadedUpdate(cacheFile, fromUpdate)
            } else {
                if (canRun.get()) {
                    throw DownloadInvalidFileSizeException(cacheFile, currentSize, totalSize)
                } else {
                    throw DownloadCancelledException(url)
                }
            }
        } else if (cacheFileSize == totalSize) {
            Timber.v("[Download] Download \"$url\"... 100%")
            DownloadedUpdate(cacheFile, fromUpdate)
        } else {
            throw DownloadInvalidFileSizeException(cacheFile, cacheFileSize, totalSize)
        }
    }

    private fun persistDownloadedUpdates(
        downloadedUpdates: List<DownloadedUpdate>
    ) {
        if (downloadedUpdates.isNotEmpty()) {
            Timber.v("[Download] Persist the downloaded updates")

            val jsonBuilder = ApkUpdater.jsonBuilder()
            val jsonType = Types.newParameterizedType(List::class.java, DownloadedUpdate::class.java)
            val jsonAdapter = jsonBuilder.adapter<List<DownloadedUpdate>>(jsonType)
            val jsonText = jsonAdapter.toJson(downloadedUpdates)

            val diskCache = ApkUpdater.downloadedUpdateDiskCache()
            if (diskCache.isClosed) {
                diskCache.open()
            }
            val editor = diskCache.edit(ApkUpdater.KEY_DOWNLOADED_UPDATES)
            val editorFile = editor.getFile(0)
            editorFile.writeText(jsonText)
        }
    }
}