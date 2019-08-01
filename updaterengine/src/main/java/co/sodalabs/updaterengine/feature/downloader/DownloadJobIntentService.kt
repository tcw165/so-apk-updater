package co.sodalabs.updaterengine.feature.downloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.JobIntentService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import co.sodalabs.updaterengine.exception.DownloadCancelledException
import co.sodalabs.updaterengine.exception.DownloadFileIOException
import co.sodalabs.updaterengine.exception.DownloadHttpException
import co.sodalabs.updaterengine.exception.DownloadSizeUnknownException
import co.sodalabs.updaterengine.exception.DownloadTimeoutException
import co.sodalabs.updaterengine.exception.DownloadUnknownErrorException
import co.sodalabs.updaterengine.exception.HttpMalformedURIException
import co.sodalabs.updaterengine.exception.HttpTooManyRedirectsException
import co.sodalabs.updaterengine.extension.mbToBytes
import co.sodalabs.updaterengine.feature.downloader.data.DownloadFailureCause
import co.sodalabs.updaterengine.feature.downloadmanager.DefaultRetryPolicy
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadManager
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadRequest
import co.sodalabs.updaterengine.feature.downloadmanager.DownloadStatusListenerV1
import co.sodalabs.updaterengine.feature.downloadmanager.ThinDownloadManager
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import co.sodalabs.updaterengine.utils.BuildUtils
import co.sodalabs.updaterengine.utils.StorageUtils
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

private const val BROADCAST_INTERVAL = 300L

class DownloadJobIntentService : JobIntentService() {

    companion object {

        fun downloadNow(
            context: Context,
            updates: List<AppUpdate>
        ) {
            val intent = Intent(context, DownloadJobIntentService::class.java)
            intent.action = IntentActions.ACTION_DOWNLOAD_UPDATES

            val packageNames = updates.map { it.packageName }
            intent.putStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES, ArrayList(packageNames))
            val downloadURIs = updates.map { Uri.parse(it.downloadUrl) }
            intent.putParcelableArrayListExtra(IntentActions.PROP_APP_DOWNLOAD_URIS, ArrayList(downloadURIs))

            enqueueWork(context, ComponentName(context, DownloadJobIntentService::class.java), UpdaterJobs.JOB_ID_DOWNLOAD_UPDATES, intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        injectDependencies()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_DOWNLOAD_UPDATES -> {
                val packageNames = intent.getStringArrayListExtra(IntentActions.PROP_APP_PACKAGE_NAMES)
                val downloadURIs = intent.getParcelableArrayListExtra<Uri>(IntentActions.PROP_APP_DOWNLOAD_URIS)
                download(packageNames, downloadURIs)
            }
            else -> throw IllegalArgumentException("Hey develop, DownloadJobIntentService is for downloading the app only!")
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
        DiskLruCache.open(
            File(StorageUtils.getCacheDirectory(this, true), CACHE_DIR),
            CACHE_JOURNAL_VERSION,
            1,
            CACHE_SIZE_MB.mbToBytes())
    }

    private val downloadManager by lazy {
        val loggingEnabled = BuildUtils.isDebug()
        ThinDownloadManager(loggingEnabled, THREAD_POOL_SIZE, diskCache)
    }

    /**
     * We use local broadcast to notify the async result.
     */
    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    private fun download(
        packageNames: List<String>,
        downloadURIs: List<Uri>
    ) {
        // The latch for joint the thread of the JobIntentService and the
        // threads of download manager.
        val countdownLatch = CountDownLatch(downloadURIs.size)

        val downloadFileURIs = mutableListOf<Uri>()
        val downloadFileIndices = mutableListOf<Int>()
        val failedDownloads = mutableListOf<Throwable>()

        try {
            for (i in 0 until downloadURIs.size) {
                val packageName = packageNames[i]
                val uri = downloadURIs[i]
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

                            // Add to the success pool
                            downloadFileURIs.add(downloadRequest.destinationURI)
                            downloadFileIndices.add(i)

                            countdownLatch.countDown()
                        }

                        override fun onDownloadFailed(
                            downloadRequest: DownloadRequest,
                            errorCode: Int,
                            errorMessage: String?
                        ) {
                            val id = downloadRequest.downloadId
                            Timber.e("[Download] Download(ID: $id) fails \"$$packageName\", error code: $errorCode")

                            // Add to the failure pool
                            failedDownloads.add(errorCode.toError(packageName, uri.path!!))

                            // Countdown for failure as well so that we could let the joint thread continue.
                            countdownLatch.countDown()
                        }

                        override fun onProgress(
                            downloadRequest: DownloadRequest,
                            totalBytes: Long,
                            downloadedBytes: Long,
                            progress: Int
                        ) {
                            val id = downloadRequest.downloadId
                            val prettyProgress = progress.toString().padStart(3)
                            // Timber.v("[Download] Download(ID: $id, package: \"$packageName\") progress $prettyProgress")
                        }
                    })

                // set target directory
                val id = downloadManager.add(request)
                Timber.i("[Download] Download(ID: $id) for \"$packageName\" (URL: \"$uri\") starts!")
            }

            // Wait for the download manager library finishing.
            countdownLatch.await(Intervals.TIMEOUT_DOWNLOAD_HR, TimeUnit.HOURS)

            reportAllDownloads(downloadFileURIs, downloadFileIndices, failedDownloads)
            // if (failedDownloads.isNotEmpty()) {
            //     reportFailedDownloads(failedDownloads)
            // }
        } catch (error: Throwable) {
            reportTimeout(error)
        }
    }

    private fun Int.toError(
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

    private fun reportAllDownloads(
        downloadFileURIs: List<Uri>,
        downloadFileIndices: List<Int>,
        failedDownloads: List<Throwable>
    ) {
        val intent = Intent(IntentActions.ACTION_DOWNLOAD_UPDATES)

        if (failedDownloads.isNotEmpty()) {
            intent.putExtra(IntentActions.PROP_ERROR, CompositeException(failedDownloads))
        }

        intent.putParcelableArrayListExtra(IntentActions.PROP_APP_DOWNLOAD_FILE_URIS, ArrayList(downloadFileURIs))
        intent.putIntegerArrayListExtra(IntentActions.PROP_APP_DOWNLOAD_FILE_URIS_TO_UPDATE_INDICES, ArrayList(downloadFileIndices))
        broadcastManager.sendBroadcast(intent)
    }

    private fun reportFailedDownloads(
        causes: List<DownloadFailureCause>
    ) {
        for (i in 0 until causes.size) {
            val cause = causes[i]
            val error = cause.errorCode.toError(cause.packageName, cause.downloadURI.path ?: "unknown")
            val failureIntent = Intent(IntentActions.ACTION_DOWNLOAD_UPDATES)
            failureIntent.putExtra(IntentActions.PROP_ERROR, error)
            broadcastManager.sendBroadcast(failureIntent)

            // We don't want to jam the broadcast.
            Thread.sleep(BROADCAST_INTERVAL)
        }
    }

    private fun reportTimeout(
        error: Throwable
    ) {
        val failureIntent = Intent(IntentActions.ACTION_DOWNLOAD_UPDATES)
        failureIntent.putExtra(IntentActions.PROP_ERROR, error)
        broadcastManager.sendBroadcast(failureIntent)
    }
}