package co.sodalabs.updaterengine.feature.downloadmanager

import co.sodalabs.updaterengine.feature.downloadmanager.util.Log
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache

/**
 * This class used to handles long-running HTTP downloads, User can raise a [DownloadRequest] request with multiple features.
 * The download manager will conduct the download in the background, taking care of HTTP interactions, failures  and retrying downloads
 * across connectivity changes.
 *
 * @author Mani Selvaraj
 * @author Praveen Kumar
 */
class ThinDownloadManager constructor(
    loggingEnabled: Boolean,
    threadPoolSize: Int,
    downloadCache: DiskLruCache
) : DownloadManager {

    /**
     * Download request queue takes care of handling the request based on priority.
     */
    private var requestQueue: DownloadRequestQueue? = null

    init {
        requestQueue = DownloadRequestQueue(threadPoolSize, downloadCache)
        requestQueue?.start()
        setLoggingEnabled(loggingEnabled)
    }

    /**
     * Add a new download.  The download will start automatically once the download manager is
     * ready to execute it and connectivity is available.
     *
     * @param request the parameters specifying this download
     *
     * @return an ID for the download, unique across the application.  This ID is used to make future
     * calls related to this download.
     *
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    override fun add(request: DownloadRequest?): Int {
        checkReleased("add(...) called on a released ThinDownloadManager.")
        if (request == null) {
            throw IllegalArgumentException("DownloadRequest cannot be null")
        }

        return requestQueue?.add(request) ?: throw NullPointerException("The manager is released")
    }

    override fun cancel(downloadId: Int): Int {
        checkReleased("cancel(...) called on a released ThinDownloadManager.")
        return requestQueue?.cancel(downloadId) ?: throw NullPointerException("The manager is released")
    }

    override fun cancelAll() {
        checkReleased("cancelAll() called on a released ThinDownloadManager.")
        requestQueue?.cancelAll()
    }

    override fun pause(downloadId: Int): Int {
        checkReleased("pause(...) called on a released ThinDownloadManager.")
        return requestQueue?.pause(downloadId) ?: throw NullPointerException("The manager is released")
    }

    override fun pauseAll() {
        checkReleased("pauseAll() called on a released ThinDownloadManager.")
        requestQueue?.pauseAll()
    }

    override fun query(downloadId: Int): Int {
        checkReleased("query(...) called on a released ThinDownloadManager.")
        return requestQueue?.query(downloadId) ?: throw NullPointerException("The manager is released")
    }

    override fun release() {
        if (!isReleased) {
            requestQueue?.release()
            requestQueue = null
        }
    }

    override fun isReleased(): Boolean {
        return requestQueue == null
    }

    /**
     * This is called by methods that want to throw an exception if the DownloadManager
     * has already been released.
     */
    private fun checkReleased(errorMessage: String) {
        if (isReleased) {
            throw IllegalStateException(errorMessage)
        }
    }

    private fun setLoggingEnabled(enabled: Boolean) {
        Log.setEnabled(enabled)
    }
}