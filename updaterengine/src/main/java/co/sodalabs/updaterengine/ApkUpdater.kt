package co.sodalabs.updaterengine

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.support.annotation.Keep
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.installer.InstallerService
import co.sodalabs.updaterengine.net.ApkCache
import java.util.concurrent.TimeUnit

class ApkUpdater private constructor(
    private val application: Application,
    private var config: ApkUpdater.Config
) {

    @Keep
    companion object {

        @Volatile
        private var singleton: ApkUpdater? = null

        fun install(app: Application, config: Config) {
            if (singleton == null) {
                synchronized(ApkUpdater::class.java) {
                    if (singleton == null) {
                        val instance = ApkUpdater(app, config)

                        instance.callback = config.callback
                        instance.scheduleUpdateChecks()
                        singleton = instance
                    }
                }
            }
        }

        fun updateConfig(config: Config) {
            singleton?.config = config
            singleton?.scheduleUpdateChecks()
        }

        fun setCallback(callback: OnUpdateAvailableCallback?) {
            singleton().callback = callback
        }

        fun checkForUpdatesNow() {
            singleton().checkForUpdate()
        }

        fun download(apk: Apk) {
            singleton().downloadApk(apk)
        }

        fun install(apk: Apk) {
            singleton().installApk(apk)
        }

        internal fun singleton(): ApkUpdater {
            return singleton ?: throw IllegalStateException("Must Initialize ApkUpdater before using singleton()")
        }
    }

    private var callback: OnUpdateAvailableCallback? = null
    private val downloader by lazy { Downloader(application) }

    private fun scheduleUpdateChecks() {
        val updateUri = constructUpdateUrl()
        UpdaterService.schedule(application, config.interval, updateUri.toString())
    }

    private fun checkForUpdate() {
        val updateUri = constructUpdateUrl()
        UpdaterService.checkNow(application, updateUri.toString())
    }

    internal fun downloadApk(appUpdate: AppUpdate) {
        val uri = Uri.parse(appUpdate.downloadUrl)
        val apk = Apk(
            uri,
            appUpdate.packageName,
            appUpdate.versionName,
            appUpdate.versionCode,
            appUpdate.hash,
            apkName = appUpdate.fileName
        )
        downloadApk(apk)
    }

    internal fun downloadApk(apk: Apk) {
        downloader.startDownload(apk)
    }

    internal fun installApk(apk: Apk) {
        val uri = apk.downloadUri
        val localApkFile = ApkCache.getApkDownloadPath(application, uri)

        if (!localApkFile.exists()) {
            downloadApk(apk)
        } else {
            val localApkUri = Uri.fromFile(localApkFile)
            InstallerService.install(application, localApkUri, uri, apk)
        }
    }

    internal fun notifyUpdateAvailable(apk: Apk, updateMessage: String): Boolean {
        ensureMainThread()
        return callback?.onUpdateAvailable(apk, updateMessage) ?: false
    }

    internal fun notifyUpdateDownloaded(apk: Apk): Boolean {
        ensureMainThread()
        return callback?.onUpdateDownloaded(apk) ?: false
    }

    internal fun notifyUpdateDownloadFailed(apk: Apk, reason: String) {
        ensureMainThread()
        callback?.onUpdateDownloadFailed(apk, reason)
    }

    private fun constructUpdateUrl(): Uri {
        val builder = Uri.parse(config.baseUrl).buildUpon()
        if (!(config.baseUrl.endsWith("/apps") || config.baseUrl.endsWith("/apps/"))) {
            builder.appendPath("apps")
        }

        // TODO: Support multiple checks at once
        builder.appendPath(config.packageNames.first())
        builder.appendPath("latest")

        return builder.build()
    }

    @Keep
    class Config(
        context: Context,
        internal val baseUrl: String
    ) {

        internal var packageNames: Array<String> = arrayOf(context.packageName)
            private set
        internal var interval: Long = TimeUnit.DAYS.toMillis(1)
            private set
        internal var callback: OnUpdateAvailableCallback? = null
            private set

        fun setUpdateInterval(interval: Long): Config {
            if (interval <= 0) {
                throw IllegalArgumentException("Interval must be greater than zero.")
            }

            this.interval = interval
            return this
        }

        fun setPackageName(packageName: String): Config {
            // TODO: Support multiple checks at once
            this.packageNames = arrayOf(packageName)
            return this
        }

        fun setCallback(callback: OnUpdateAvailableCallback): Config {
            this.callback = callback
            return this
        }
    }

    private fun ensureMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw IllegalStateException("Must be run on main thread.")
        }
    }

    @Keep
    interface OnUpdateAvailableCallback {
        /**
         * Callback when an update is available. Return true to download the file, false otherwise.
         */
        fun onUpdateAvailable(apk: Apk, updateMessage: String): Boolean

        /**
         * Callback when an update is downloaded. Return true to install the file, false otherwise.
         */
        fun onUpdateDownloaded(apk: Apk): Boolean

        /**
         * An error has occurred during file download.
         */
        fun onUpdateDownloadFailed(apk: Apk, reason: String)
    }
}