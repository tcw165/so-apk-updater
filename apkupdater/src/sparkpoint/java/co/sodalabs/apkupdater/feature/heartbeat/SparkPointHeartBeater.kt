package co.sodalabs.apkupdater.feature.heartbeat

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.core.app.JobIntentService
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.HTTPResponseCode
import io.reactivex.Observable
import timber.log.Timber
import javax.inject.Inject

private const val INITIAL_CHECK_DELAY_MILLIS = 1000L // 1 second

class SparkPointHeartBeater @Inject constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : UpdaterHeartBeater {

    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private val jobScheduler by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        } else {
            throw IllegalStateException("VERSION.SDK_INT < LOLLIPOP")
        }
    }

    override fun sendHeartBeatNow() {
        val intent = Intent(context, HeartBeatJobIntentService::class.java)
        intent.action = IntentActions.ACTION_SEND_HEART_BEAT_NOW
        JobIntentService.enqueueWork(context, ComponentName(context, HeartBeatJobIntentService::class.java), UpdaterJobs.JOB_ID_HEART_BEAT, intent)
    }

    override fun scheduleRecurringHeartBeat(
        intervalMs: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Timber.v("[HeartBeat] (< 21) Schedule a recurring update, using AlarmManager")

            val intent = Intent(context, HeartBeatJobIntentService::class.java)
            intent.action = IntentActions.ACTION_SEND_HEART_BEAT_NOW

            val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)

            alarmManager.cancel(pendingIntent)
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INITIAL_CHECK_DELAY_MILLIS,
                intervalMs,
                pendingIntent
            )
        } else {
            Timber.v("[HeartBeat] (>= 21) Schedule a recurring update, using android-21 JobScheduler")

            val componentName = ComponentName(context, HeartBeatJobIntentService::class.java)
            val bundle = PersistableBundle()
            // < You could append data to it here

            val builder = JobInfo.Builder(UpdaterJobs.JOB_ID_HEART_BEAT, componentName)
                .setRequiresDeviceIdle(false)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(intervalMs)
                .setExtras(bundle)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
            }

            // Note: The job would be consumed by CheckJobService and translated
            // to an Intent. Then the Intent is handled here in onHandleWork()!
            jobScheduler.cancel(UpdaterJobs.JOB_ID_HEART_BEAT)
            jobScheduler.schedule(builder.build())
        }
    }

    override fun cancelRecurringHeartBeat() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Timber.v("[HeartBeat] (< 21) Cancel a recurring update, using AlarmManager")

            val intent = Intent(context, HeartBeatJobIntentService::class.java).apply {
                action = IntentActions.ACTION_SEND_HEART_BEAT_NOW
            }
            val pendingIntent: PendingIntent? = PendingIntent.getService(context, 0, intent,
                PendingIntent.FLAG_NO_CREATE or
                    PendingIntent.FLAG_CANCEL_CURRENT)

            pendingIntent?.let {
                alarmManager.cancel(it)
            }
        } else {
            Timber.v("[HeartBeat] (>= 21) Cancel a recurring update, using android-21 JobScheduler")

            jobScheduler.cancel(UpdaterJobs.JOB_ID_HEART_BEAT)
        }
    }

    override fun observeRecurringHeartBeat(): Observable<Int> {
        // Observe the result via the local broadcast
        val intentFilter = IntentFilter(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
        return RxLocalBroadcastReceiver.bind(context, intentFilter)
            .subscribeOn(schedulers.main())
            .map { intent -> validateApiResponse(intent) }
    }

    private fun validateApiResponse(
        intent: Intent
    ): Int {
        val code = intent.getIntExtra(IntentActions.PROP_HTTP_RESPONSE_CODE, HTTPResponseCode.Unknown.code)
        if (code == HTTPResponseCode.Unknown.code) {
            val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
            error?.let {
                // Throw the error in client
                throw it
            }
        }

        return code
    }
}