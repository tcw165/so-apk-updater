package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import android.net.Uri
import co.sodalabs.fitdownloadmanager.DefaultRetryPolicy
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_DOWNLOAD_CANCELLED
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_DOWNLOAD_SIZE_UNKNOWN
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_FILE_ERROR
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_HTTP_DATA_ERROR
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_MALFORMED_URI
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_TOO_MANY_REDIRECTS
import co.sodalabs.fitdownloadmanager.DownloadManager.ERROR_UNHANDLED_HTTP_CODE
import co.sodalabs.fitdownloadmanager.DownloadRequest
import co.sodalabs.fitdownloadmanager.DownloadStatusListenerV1
import co.sodalabs.fitdownloadmanager.FitDownloadManager
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.exception.DownloadCancelledException
import co.sodalabs.updaterengine.exception.DownloadFileIOException
import co.sodalabs.updaterengine.exception.DownloadHttpException
import co.sodalabs.updaterengine.exception.DownloadSizeUnknownException
import co.sodalabs.updaterengine.exception.DownloadTimeoutException
import co.sodalabs.updaterengine.exception.DownloadUnknownErrorException
import co.sodalabs.updaterengine.exception.HttpMalformedURIException
import co.sodalabs.updaterengine.exception.HttpTooManyRedirectsException
import co.sodalabs.updaterengine.net.ApkCache
import co.sodalabs.updaterengine.utils.BuildUtils
import io.reactivex.Single
import io.reactivex.SingleEmitter
import timber.log.Timber
import java.io.File

private const val MAX_RETRY_COUNT = 3
private const val BACKOFF_MULTIPLIER = 2f

class DefaultUpdatesDownloader constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesDownloader {

    private val downloadManager by lazy {
        val loggingEnabled = BuildUtils.isDebug()
        FitDownloadManager(loggingEnabled)
    }

    override fun download(
        updates: List<AppUpdate>
    ): Single<List<Apk>> {
        val tasks = updates.map { createDownloadSingle(it) }

        return Single.zip(tasks) { arr ->
            val out = mutableListOf<Apk>()
            arr.forEach {
                if (it is Apk) {
                    out.add(it)
                }
            }
            out
        }
    }

    private fun createDownloadSingle(
        update: AppUpdate
    ): Single<Apk> {
        return Single
            .create { emitter: SingleEmitter<Apk> ->
                val uri = Uri.parse(update.downloadUrl)
                // val apkFilePath = ApkCache.getApkDownloadPath(context, uri)
                // TODO: val apkFileSize = apkFilePath.length()

                // if (apkFilePath.exists()) {
                //     Timber.v("[Download] cache found! file: $apkFilePath, package: \"${update.packageName}\"")
                //     emitter.onSuccess(Apk(
                //         file = apkFilePath,
                //         fromUpdate = update
                //     ))
                // } else {
                val request = DownloadRequest(uri)
                    .setRetryPolicy(DefaultRetryPolicy(DefaultRetryPolicy.DEFAULT_TIMEOUT_MS, MAX_RETRY_COUNT, BACKOFF_MULTIPLIER))
                    .setPriority(DownloadRequest.Priority.LOW)
                    .setStatusListener(object : DownloadStatusListenerV1 {

                        override fun onDownloadComplete(
                            downloadRequest: DownloadRequest
                        ) {
                            val id = downloadRequest.downloadId
                            val file = File(downloadRequest.destinationURI.path)
                            Timber.v("[Download] Download (ID: $id) finishes! {file: $file, package: \"${update.packageName}\", URL: \"${update.downloadUrl}\"")
                            emitter.onSuccess(Apk(
                                file = file,
                                fromUpdate = update
                            ))
                        }

                        override fun onDownloadFailed(
                            downloadRequest: DownloadRequest,
                            errorCode: Int,
                            errorMessage: String?
                        ) {
                            val id = downloadRequest.downloadId
                            Timber.e("[Download] Download(ID: $id) fails \"${update.packageName}\", error code: $errorCode")
                            emitter.onError(errorCode.toError(
                                update.packageName,
                                update.downloadUrl))
                        }

                        override fun onProgress(
                            downloadRequest: DownloadRequest,
                            totalBytes: Long,
                            downloadedBytes: Long,
                            progress: Int
                        ) {
                            val id = downloadRequest.downloadId
                            val prettyProgress = progress.toString().padStart(3)
                            Timber.v("[Download] Download(ID: $id) progress $prettyProgress on \"${update.packageName}\"")
                        }
                    })

                // set target directory
                val destFile = ApkCache.getApkDownloadPath(context, uri)
                request.destinationURI = Uri.fromFile(destFile)
                val id = downloadManager.add(request)
                Timber.i("[Download] Download(ID: $id) for \"${update.packageName}\" (URL: \"${update.downloadUrl}\") starts!")

                // Download cancel handling.
                emitter.setCancellable { downloadManager.cancel(id) }
                // }
            }
            .subscribeOn(schedulers.io())
    }

    private fun Int.toError(
        packageName: String,
        downloadURL: String
    ): Throwable {
        return when (this) {
            ERROR_CONNECTION_TIMEOUT_AFTER_RETRIES -> DownloadTimeoutException(packageName, downloadURL)
            ERROR_DOWNLOAD_CANCELLED -> DownloadCancelledException(packageName, downloadURL)
            ERROR_DOWNLOAD_SIZE_UNKNOWN -> DownloadSizeUnknownException(packageName, downloadURL)
            ERROR_MALFORMED_URI -> HttpMalformedURIException(downloadURL)
            ERROR_FILE_ERROR -> DownloadFileIOException(packageName, downloadURL)
            ERROR_HTTP_DATA_ERROR -> DownloadHttpException(packageName, downloadURL)
            ERROR_TOO_MANY_REDIRECTS -> HttpTooManyRedirectsException(downloadURL)
            ERROR_UNHANDLED_HTTP_CODE -> DownloadUnknownErrorException(packageName, downloadURL)
            else -> IllegalArgumentException("Unknown error code: $this")
        }
    }
}