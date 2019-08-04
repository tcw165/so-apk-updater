package co.sodalabs.updaterengine.feature.downloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.JobIntentService
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.DownloadCancelledException
import co.sodalabs.updaterengine.exception.DownloadFileIOException
import co.sodalabs.updaterengine.exception.DownloadHttpException
import co.sodalabs.updaterengine.exception.DownloadSizeUnknownException
import co.sodalabs.updaterengine.exception.DownloadTimeoutException
import co.sodalabs.updaterengine.exception.DownloadUnknownErrorException
import co.sodalabs.updaterengine.exception.HttpMalformedURIException
import co.sodalabs.updaterengine.exception.HttpTooManyRedirectsException
import co.sodalabs.updaterengine.extension.mbToBytes
import co.sodalabs.updaterengine.feature.core.AppUpdaterService
import co.sodalabs.updaterengine.feature.downloadmanager.DefaultRetryPolicy
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadManager
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadRequest
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadStatusListenerV1
import co.sodalabs.updaterengine.feature.downloadmanager.ThinDownloadManager
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import co.sodalabs.updaterengine.utils.BuildUtils
import co.sodalabs.updaterengine.utils.StorageUtils
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import timber.log.Timber
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val CACHE_DIR = "apks"
private const val CACHE_JOURNAL_VERSION = 1

private const val THREAD_POOL_SIZE = 3
private const val CACHE_SIZE_MB = 1024

private const val MAX_RETRY_COUNT = 3
private const val BACKOFF_MULTIPLIER = 2f

class DownloadJobIntentService : JobIntentService() {

    companion object {

        fun downloadNow(
            context: Context,
            updates: List<AppUpdate>
        ) {
            val intent = Intent(context, DownloadJobIntentService::class.java)
            intent.action = IntentActions.ACTION_DOWNLOAD_UPDATES
            intent.putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))

            enqueueWork(context, ComponentName(context, DownloadJobIntentService::class.java), UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES, intent)
        }
    }

    private val disposables = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        injectDependencies()

        observeDownloadProgress()
    }

    override fun onDestroy() {
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

    // DI /////////////////////////////////////////////////////////////////////

    private fun injectDependencies() {
        // No-op
    }

    // Download ///////////////////////////////////////////////////////////////

    // TODO: Shall we close the cache when the app is killed?
    private val diskCache by lazy {
        // The cache dir would be "/storage/emulated/legacy/co.sodalabs.apkupdater/apks/apks/"
        DiskLruCache(
            File(StorageUtils.getCacheDirectory(this, true), CACHE_DIR),
            CACHE_JOURNAL_VERSION,
            1,
            CACHE_SIZE_MB.mbToBytes()
        )
    }

    private val downloadManager by lazy {
        val loggingEnabled = BuildUtils.isDebug()
        ThinDownloadManager(loggingEnabled, THREAD_POOL_SIZE, diskCache)
    }

    private fun download(
        updates: List<AppUpdate>
    ) {
        // The latch for joint the thread of the JobIntentService and the
        // threads of download manager.
        val countdownLatch = CountDownLatch(updates.size)

        val downloadedUpdates = mutableListOf<DownloadedUpdate>()
        val aggregateProgresses = mutableListOf<DownloadProgress>()
        val errors = mutableListOf<Throwable>()
        val requests = mutableListOf<DownloadRequest>()

        // Delete the cache before downloading if we don't use cache.
        if (!ApkUpdater.downloadUseCache()) {
            diskCache.delete()
        }
        // Open the cache
        if (!diskCache.isOpened) {
            diskCache.open()
        }

        try {
            // Prepare the download request
            for (i in 0 until updates.size) {
                val update = updates[i]
                val packageName = update.packageName
                val url = update.downloadUrl
                val uri = Uri.parse(url)
                val request = DownloadRequest(uri)
                    .setRetryPolicy(DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, MAX_RETRY_COUNT, BACKOFF_MULTIPLIER))
                    .setPriority(DownloadRequest.Priority.LOW)
                    .setStatusListener(object : DownloadStatusListenerV1 {

                        override fun onDownloadComplete(
                            downloadRequest: DownloadRequest
                        ) {
                            val id = downloadRequest.downloadId
                            val file = File(downloadRequest.destinationURI.path)
                            Timber.v("[Download] Download (ID: $id) finishes! {file: $file, package: \"$packageName\", URL: \"$uri\"")

                            synchronized(countdownLatch) {
                                // Add to the success pool
                                downloadedUpdates.add(DownloadedUpdate(
                                    file = file,
                                    fromUpdate = update
                                ))
                            }

                            countdownLatch.countDown()
                        }

                        override fun onDownloadFailed(
                            downloadRequest: DownloadRequest,
                            errorCode: Int,
                            errorMessage: String?
                        ) {
                            val id = downloadRequest.downloadId
                            Timber.e("[Download] Download(ID: $id) fails \"$$packageName\", error code: $errorCode")

                            synchronized(countdownLatch) {
                                // Add to the failure pool
                                errors.add(errorCode.errorCodeToError(packageName, uri.path!!))
                            }

                            // Countdown for failure as well so that we could let the joint thread continue.
                            countdownLatch.countDown()
                        }

                        override fun onProgress(
                            downloadRequest: DownloadRequest,
                            totalBytes: Long,
                            downloadedBytes: Long,
                            progress: Int
                        ) {
                            logDownloadProgress(downloadRequest, progress, aggregateProgresses)
                        }
                    })

                requests.add(request)
                aggregateProgresses.add(DownloadProgress(
                    downloadURL = url,
                    downloadProgress = 0
                ))
            }

            // Enqueue the download request
            for (i in 0 until requests.size) {
                val request = requests[i]
                val uri = request.uri
                val id = downloadManager.add(request)
                Timber.v("[Download] Download(ID: $id) for \"$packageName\" (URL: \"$uri\") starts!")
            }

            // Wait for the download manager library finishing.
            countdownLatch.await(Intervals.TIMEOUT_DOWNLOAD_HR, TimeUnit.HOURS)

            AppUpdaterService.notifyDownloadsComplete(this, downloadedUpdates, errors)
        } catch (error: Throwable) {
            AppUpdaterService.notifyDownloadsComplete(this, emptyList(), listOf(error))
        }
    }

    private fun Int.errorCodeToError(
        packageName: String,
        downloadURL: String
    ): Throwable {
        return when (this) {
            DownloadManager.ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES -> DownloadTimeoutException(packageName, downloadURL)
            DownloadManager.ERROR_DOWNLOAD_CANCELLED -> DownloadCancelledException(packageName, downloadURL)
            DownloadManager.ERROR_DOWNLOAD_SIZE_UNKNOWN -> DownloadSizeUnknownException(packageName, downloadURL)
            DownloadManager.ERROR_MALFORMED_URI -> HttpMalformedURIException(downloadURL)
            DownloadManager.ERROR_FILE_ERROR -> DownloadFileIOException(packageName, downloadURL)
            DownloadManager.ERROR_HTTP_DATA_ERROR -> DownloadHttpException(packageName, downloadURL)
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> HttpTooManyRedirectsException(downloadURL)
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> DownloadUnknownErrorException(packageName, downloadURL)
            else -> IllegalArgumentException("Unknown error code: $this")
        }
    }

    // Progress ///////////////////////////////////////////////////////////////

    private val progressRelay = PublishRelay.create<List<DownloadProgress>>().toSerialized()

    private fun observeDownloadProgress() {
        val sb = StringBuilder()
        progressRelay
            .sample(Intervals.SAMPLE_INTERVAL_LONG, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ progressList ->
                sb.clear()
                sb.appendln("[Download] download progress: [")
                for (i in 0 until progressList.size) {
                    val progress = progressList[i]
                    val url = progress.downloadURL
                    val prettyProgress = progress.downloadProgress.toString().padStart(3)
                    sb.appendln("    download request $url is at $prettyProgress percentage,")
                }
                sb.appendln("]")
                Timber.v(sb.toString())
            }, Timber::e)
            .addTo(disposables)
    }

    private fun logDownloadProgress(
        downloadRequest: DownloadRequest,
        progress: Int,
        aggregateProgresses: MutableList<DownloadProgress>
    ) {
        val found = aggregateProgresses.indexOfFirst { it.downloadURL == downloadRequest.uri.toString() }

        if (found in 0 until aggregateProgresses.size) {
            val newProgress = aggregateProgresses[found].copy(downloadProgress = progress)
            aggregateProgresses[found] = newProgress

            progressRelay.accept(aggregateProgresses.toList())
        }
    }

    private data class DownloadProgress(
        val downloadURL: String,
        val downloadProgress: Int
    )
}