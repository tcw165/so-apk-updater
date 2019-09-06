package co.sodalabs.updaterengine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

object UpdaterNotificationFactory {

    fun create(
        context: Context,
        notificationChannelID: String
    ): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context, notificationChannelID)
        } else {
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            notificationChannelID
        }

        val builder = NotificationCompat.Builder(context, channelId)
        builder.setWhen(System.currentTimeMillis())
        builder.setContentTitle(context.getString(R.string.notification_title))
        builder.setOngoing(true)
        builder.priority = NotificationCompat.PRIORITY_MIN
        builder.setCategory(NotificationCompat.CATEGORY_SERVICE)

        // val notifyIntent = Intent(this, UpdaterService::class.java)
        // val notifyPendingIntent = PendingIntent.get(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        // builder.setContentIntent(notifyPendingIntent)

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(
        context: Context,
        notificationChannelID: String
    ): String {
        val channelName = context.getString(R.string.notification_channel_name)
        val chan = NotificationChannel(
            notificationChannelID,
            channelName,
            NotificationManager.IMPORTANCE_NONE
        )
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(chan)
        return notificationChannelID
    }
}