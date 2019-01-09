package co.sodalabs.updaterengine

import android.content.Context
import android.net.Uri
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.SanitizedFile
import co.sodalabs.updaterengine.net.ApkCache
import co.sodalabs.updaterengine.utils.BuildUtils
import co.sodalabs.updaterengine.utils.runOnUiThread
import com.thin.downloadmanager.DefaultRetryPolicy
import com.thin.downloadmanager.DefaultRetryPolicy.DEFAULT_TIMEOUT_MS
import com.thin.downloadmanager.DownloadManager
import com.thin.downloadmanager.DownloadRequest
import com.thin.downloadmanager.DownloadStatusListenerV1
import com.thin.downloadmanager.ThinDownloadManager
import timber.log.Timber

private const val MAX_RETRY_COUNT = 3
private const val BACKOFF_MULTIPLIER = 2f

class Downloader(
    private val context: Context
) {

    private val downloadManager by lazy {
        val loggingEnabled = BuildUtils.isDebug()
        ThinDownloadManager(loggingEnabled)
    }

    fun startDownload(apk: Apk) {
        val uri = apk.downloadUri
        val apkFilePath = ApkCache.getApkDownloadPath(context, uri)
        // TODO: val apkFileSize = apkFilePath.length()

        if (apkFilePath.exists()) {
            handleDownloadSuccess(uri, apkFilePath, apk)
        } else {
            download(uri, apk)
        }
    }

    private fun download(uri: Uri, apk: Apk): Int {
        val request = DownloadRequest(uri)
            .setRetryPolicy(DefaultRetryPolicy(DEFAULT_TIMEOUT_MS, MAX_RETRY_COUNT, BACKOFF_MULTIPLIER))
            .setPriority(DownloadRequest.Priority.LOW)
            .setStatusListener(object : DownloadStatusListenerV1 {
                private val NO_PROGRESS = -1
                private var innerProgress = -1

                override fun onDownloadComplete(downloadRequest: DownloadRequest) {
                    val resultPath = ApkCache.getApkDownloadPath(context, uri)
                    Timber.i("Download complete: ${downloadRequest.downloadId}")
                    handleDownloadSuccess(downloadRequest.uri, resultPath, apk)
                    innerProgress = NO_PROGRESS
                }

                override fun onDownloadFailed(downloadRequest: DownloadRequest, errorCode: Int, errorMessage: String?) {
                    Timber.i("Download complete: ${downloadRequest.downloadId}")
                    handleDownloadFailed(downloadRequest.uri, errorCode, apk)
                    innerProgress = NO_PROGRESS
                }

                override fun onProgress(
                    downloadRequest: DownloadRequest,
                    totalBytes: Long,
                    downloadedBytes: Long,
                    progress: Int
                ) {
                    if (progress > innerProgress) {
                        innerProgress = progress
                        Timber.v("Progress: $progress%")
                    }
                }
            })

        // set target directory
        val destFile = ApkCache.getApkDownloadPath(context, uri)
        request.destinationURI = Uri.fromFile(destFile)

        Timber.v("Downloading ${request.uri}\nDestination: ${request.destinationURI}")
        val id = downloadManager.add(request)
        Timber.i("Start download, id = $id")
        return id
    }

    private fun handleDownloadSuccess(
        uri: Uri,
        resultPath: SanitizedFile,
        apk: Apk
    ) {
        Timber.d("Download successful: $uri - $resultPath")

        context.runOnUiThread {
            val autoInstall = ApkUpdater.singleton().notifyUpdateDownloaded(apk)

            if (autoInstall) {
                ApkUpdater.singleton().installApk(apk)
            }
        }
    }

    private fun handleDownloadFailed(uri: Uri, reason: Int, apk: Apk) {
        val failedReason = when (reason) {
            DownloadManager.ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES -> "ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES"
            DownloadManager.ERROR_DOWNLOAD_CANCELLED -> "ERROR_DOWNLOAD_CANCELLED"
            DownloadManager.ERROR_DOWNLOAD_SIZE_UNKNOWN -> "ERROR_DOWNLOAD_SIZE_UNKNOWN"
            DownloadManager.ERROR_MALFORMED_URI -> "ERROR_MALFORMED_URI"
            DownloadManager.ERROR_FILE_ERROR -> "ERROR_FILE_ERROR"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "ERROR_HTTP_DATA_ERROR"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "ERROR_TOO_MANY_REDIRECTS"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "ERROR_UNHANDLED_HTTP_CODE"
            else -> "ERROR_UNKNOWN"
        }

        Timber.w("Download failed: $uri - $failedReason")
        context.runOnUiThread {
            ApkUpdater.singleton().notifyUpdateDownloadFailed(apk, failedReason)
        }
    }
}