package co.sodalabs.updaterengine.feature.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.IFirmwareCheckCallback
import co.sodalabs.updaterengine.IFirmwareInstallCallback
import co.sodalabs.updaterengine.IUpdaterService
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.R
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.exception.CompositeException
import timber.log.Timber

private const val NOTIFICATION_CHANNEL_ID = "updater_engine"
private const val NOTIFICATION_ID = 20190802

class AppUpdaterService : Service() {

    companion object {

        private val uiHandler = Handler(Looper.getMainLooper())

        fun start(
            context: Context
        ) {
            uiHandler.post {
                val serviceIntent = Intent(context, AppUpdaterService::class.java)
                serviceIntent.action = IntentActions.ACTION_ENGINE_START
                context.startService(serviceIntent)
            }
        }

        /**
         * The method for the updater engine knows the update check finishes and
         * to move on.
         */
        fun notifyUpdateCheckComplete(
            context: Context,
            updates: List<AppUpdate>,
            updatesError: Throwable?
        ) {
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForCheckComplete(updates, updatesError)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, AppUpdaterService::class.java)
                serviceIntent.prepareForCheckComplete(updates, updatesError)
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForCheckComplete(
            updates: List<AppUpdate>,
            updatesError: Throwable?
        ) {
            this.apply {
                action = IntentActions.ACTION_CHECK_UPDATES
                // Result
                if (updates.isNotEmpty()) {
                    putParcelableArrayListExtra(IntentActions.PROP_FOUND_UPDATES, ArrayList(updates))
                }
                // Error
                updatesError?.let { error ->
                    putExtra(IntentActions.PROP_ERROR, error)
                }
            }
        }

        /**
         * The method for the updater engine knows the download finishes and
         * to move on.
         */
        fun notifyDownloadsComplete(
            context: Context,
            downloadedUpdates: List<DownloadedUpdate>,
            errors: List<Throwable>
        ) {
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForDownloadComplete(downloadedUpdates, errors)
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, AppUpdaterService::class.java)
                serviceIntent.prepareForDownloadComplete(downloadedUpdates, errors)
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForDownloadComplete(
            downloadedUpdates: List<DownloadedUpdate>,
            errors: List<Throwable>
        ) {
            this.apply {
                action = IntentActions.ACTION_DOWNLOAD_UPDATES
                // Result
                putParcelableArrayListExtra(IntentActions.PROP_DOWNLOADED_UPDATES, ArrayList(downloadedUpdates))
                // Error
                if (errors.isNotEmpty()) {
                    putExtra(IntentActions.PROP_ERROR, CompositeException(errors))
                }
            }
        }

        /**
         * The method for the updater engine knows the install finishes and
         * to move on.
         */
        fun notifyInstallComplete(
            context: Context
        ) {
            uiHandler.post {
                val broadcastIntent = Intent()
                broadcastIntent.prepareForInstallComplete()
                val broadcastManager = LocalBroadcastManager.getInstance(context)
                broadcastManager.sendBroadcast(broadcastIntent)

                val serviceIntent = Intent(context, AppUpdaterService::class.java)
                serviceIntent.prepareForInstallComplete()
                context.startService(serviceIntent)
            }
        }

        private fun Intent.prepareForInstallComplete() {
            this.apply {
                action = IntentActions.ACTION_INSTALL_UPDATES
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        intent?.let { safeIntent ->
            when (safeIntent.action) {
                IntentActions.ACTION_ENGINE_START -> start()
                IntentActions.ACTION_CHECK_UPDATES -> downloadUpdatesNow(safeIntent)
                IntentActions.ACTION_DOWNLOAD_UPDATES -> scheduleInstallUpdates(safeIntent)
                IntentActions.ACTION_INSTALL_UPDATES -> postInstallUpdates(safeIntent)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Timber.v("[Updater] engine stops!")
        super.onDestroy()
    }

    // Init ///////////////////////////////////////////////////////////////////

    private fun start() {
        Timber.v("[Updater] engine starts ~~~~ .oO")
        ApkUpdater.scheduleRecurringHeartbeat()
        ApkUpdater.scheduleRecurringCheck()
    }

    // Check //////////////////////////////////////////////////////////////////

    private var checkAttempts: Int = 0

    // Download ///////////////////////////////////////////////////////////////

    private var downloadAttempts: Int = 0

    @Suppress("UNCHECKED_CAST")
    private fun downloadUpdatesNow(
        intent: Intent
    ) {
        val updatesError: Throwable? = intent.getSerializableExtra(IntentActions.PROP_ERROR) as Throwable?
        updatesError?.let {
            // TODO error-handling
            ++checkAttempts
            // ApkUpdater.scheduleCheckUpdate(Intervals.RETRY_CHECK)
        } ?: kotlin.run {
            // Reset check attempts since we successfully find the updates to download
            checkAttempts = 0

            val updates = intent.getParcelableArrayListExtra<AppUpdate>(IntentActions.PROP_FOUND_UPDATES)
            ApkUpdater.downloadUpdateNow(updates.toList())
        }
    }

    // Install ////////////////////////////////////////////////////////////////

    private fun scheduleInstallUpdates(
        intent: Intent
    ) {
        // Reset download attempts since we successfully download the updates.
        downloadAttempts = 0

        val nullableError = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
        nullableError?.let { error ->
            // TODO error-handling
        } ?: kotlin.run {
            val downloadedUpdates = intent.getParcelableArrayListExtra<DownloadedUpdate>(IntentActions.PROP_DOWNLOADED_UPDATES)
            ApkUpdater.scheduleInstallUpdates(downloadedUpdates)
        }
    }

    private fun postInstallUpdates(
        intent: Intent
    ) {
        // No-op
    }

    // Notification ///////////////////////////////////////////////////////////

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    private fun createForegroundNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            NOTIFICATION_CHANNEL_ID
        }

        val builder = NotificationCompat.Builder(this, channelId)
        builder.setWhen(System.currentTimeMillis())
        builder.setContentTitle(getString(R.string.notification_title))
        builder.setOngoing(true)
        builder.priority = NotificationCompat.PRIORITY_MIN
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)

        // val notifyIntent = Intent(this, AppUpdaterService::class.java)
        // val notifyPendingIntent = PendingIntent.get(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        // builder.setContentIntent(notifyPendingIntent)

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = getString(R.string.notification_channel_name)
        val chan = NotificationChannel(channelId,
            channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        notificationManager.createNotificationChannel(chan)
        return channelId
    }

    // IBinder ////////////////////////////////////////////////////////////////

    override fun onBind(intent: Intent?): IBinder? {
        return remoteBinder
    }

    private val remoteBinder = object : IUpdaterService.Stub() {
        override fun getCheckIntervalSecs(): Long {
            TODO("not implemented")
        }

        override fun setCheckIntervalSecs(intervalSecs: Long) {
            TODO("not implemented")
        }

        override fun getInstallStartHourOfDay(): Long {
            TODO("not implemented")
        }

        override fun setInstallStartHourOfDay(startHourOfDay: Int) {
            TODO("not implemented")
        }

        override fun getInstallEndHourOfDay(): Long {
            TODO("not implemented")
        }

        override fun setInstallEndHourOfDay(endHourOfDay: Int) {
            TODO("not implemented")
        }

        override fun checkFirmwareUpdateNow(callback: IFirmwareCheckCallback?) {
            TODO("not implemented")
        }

        override fun installFirmwareUpdate(callback: IFirmwareInstallCallback?) {
            TODO("not implemented")
        }
    }
}