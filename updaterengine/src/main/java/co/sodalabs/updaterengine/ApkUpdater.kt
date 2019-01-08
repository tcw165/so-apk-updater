package co.sodalabs.updaterengine

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.annotation.Keep
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.installer.InstallerService
import co.sodalabs.updaterengine.net.ApkCache
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
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

                        config.callback?.let { instance.callbacks?.add(it) }
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

        fun addCallback(callback: OnUpdateAvailableCallback) {
            singleton().callbacks.add(callback)
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

    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val callbacks = Collections.newSetFromMap(ConcurrentHashMap<OnUpdateAvailableCallback, Boolean>())
    private val downloader by lazy { Downloader(application) }

    private fun scheduleUpdateChecks() {
        val updateUri = constructUpdateUrl()
        UpdaterService.schedule(application, config.interval, updateUri.toString(), config.autoDownload)
    }

    private fun checkForUpdate() {
        val updateUri = constructUpdateUrl()
        UpdaterService.checkNow(application, updateUri.toString(), config.autoDownload)
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
        downloader.startDownload(apk, config.autoInstall)
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

    internal fun notifyUpdateAvailable(apk: Apk, updateMessage: String) {
        callbackExecutor.execute {
            callbacks.forEach {
                application.runOnUiThread {
                    it.onUpdateAvailable(apk, updateMessage)
                }
            }
        }
    }

    internal fun notifyUpdateDownloaded(apk: Apk) {
        callbackExecutor.execute {
            callbacks.forEach {
                application.runOnUiThread {
                    it.onUpdateDownloaded(apk)
                }
            }
        }
    }

    internal fun notifyUpdateDownloadFailed(apk: Apk, reason: String) {
        callbackExecutor.execute {
            callbacks.forEach {
                application.runOnUiThread {
                    it.onUpdateDownloadFailed(apk, reason)
                }
            }
        }
    }

    private fun Context.runOnUiThread(f: Context.() -> Unit) {
        if (ContextHelper.mainThread == Thread.currentThread()) f() else ContextHelper.handler.post { f() }
    }

    private object ContextHelper {
        val handler = Handler(Looper.getMainLooper())
        val mainThread: Thread = Looper.getMainLooper().thread
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

    class Config(
        context: Context,
        internal val baseUrl: String
    ) {

        internal var packageNames: Array<String> = arrayOf(context.packageName)
            private set
        internal var interval: Long = TimeUnit.DAYS.toMillis(1)
            private set
        internal var autoDownload = true
            private set
        internal var autoInstall = false
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

        fun setAutoDownload(auto: Boolean): Config {
            this.autoDownload = auto
            return this
        }

        fun setAutoInstall(auto: Boolean): Config {
            this.autoInstall = auto
            return this
        }

        fun setPackageName(packageName: String): Config {
            // TODO: Support multiple checks at once
            this.packageNames = arrayOf(packageName)
            return this
        }

        fun addCallback(callback: OnUpdateAvailableCallback): Config {
            this.callback = callback
            return this
        }
    }

    interface OnUpdateAvailableCallback {
        fun onUpdateAvailable(apk: Apk, updateMessage: String)
        fun onUpdateDownloaded(apk: Apk)
        fun onUpdateDownloadFailed(apk: Apk, reason: String)
    }
}