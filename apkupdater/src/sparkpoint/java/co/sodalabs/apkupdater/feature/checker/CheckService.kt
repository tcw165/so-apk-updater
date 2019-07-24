package co.sodalabs.apkupdater.feature.checker

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.core.app.JobIntentService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.feature.checker.api.ISparkPointUpdateCheckApi
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterJobs.JOB_ID_CHECK_UPDATES
import co.sodalabs.updaterengine.data.AppUpdate
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

private const val INITIAL_CHECK_DELAY = 5000L

class CheckService : JobIntentService() {

    companion object {

        fun cancelAllPendingJobs(
            context: Context
        ) {
            val intent = Intent(context, CheckService::class.java)
            intent.action = IntentActions.ACTION_CHECK_UPDATES

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("(>= 21) Cancel the pending jobs using android-21 JobScheduler")
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(JOB_ID_CHECK_UPDATES)
            } else {
                Timber.v("(< 21) Cancel the pending jobs using AlarmManager")
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            }

            // Stop the service immediately
            Timber.v("Stop the service now!")
            context.stopService(intent)
        }

        fun checkUpdatesNow(
            context: Context,
            packageNames: Array<String>
        ) {
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Timber.v("(>= 26) Schedule an immediate version-check with Service#enqueueWork()")
            val intent = Intent(context, CheckService::class.java)
            intent.action = IntentActions.ACTION_CHECK_UPDATES
            intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)
            enqueueWork(context, ComponentName(context, CheckService::class.java), JOB_ID_CHECK_UPDATES, intent)
            // } else {
            //     Timber.v("(< 26) Schedule an immediate version-check with Context#startService()")
            //     val intent = Intent(context, CheckService::class.java)
            //     intent.action = IntentActions.ACTION_CHECK_UPDATES
            //     intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)
            //     context.startService(intent)
            // }
        }

        fun scheduleUpdatesCheck(
            context: Context,
            interval: Long,
            packageNames: Array<String>
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("(< 21) Schedule a recurring update, using AlarmManager")

                val intent = Intent(context, CheckService::class.java)
                intent.action = IntentActions.ACTION_CHECK_UPDATES
                intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                // TODO: Do we need to recover the scheduling on boot?
                alarmManager.cancel(pendingIntent)
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + INITIAL_CHECK_DELAY,
                    interval,
                    pendingIntent
                )
            } else {
                Timber.v("(>= 21) Schedule a recurring update, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, CheckJobService::class.java)
                val bundle = PersistableBundle()
                bundle.putStringArray(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)

                val builder = JobInfo.Builder(JOB_ID_CHECK_UPDATES, componentName)
                    .setRequiresDeviceIdle(false)
                    .setPeriodic(interval)
                    .setExtras(bundle)

                if (Build.VERSION.SDK_INT >= 26) {
                    builder.setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                }

                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

                // Note: The job would be consumed by CheckJobService and translated
                // to an Intent. Then the Intent is handled here in onHandleWork()!
                jobScheduler.cancel(JOB_ID_CHECK_UPDATES)
                jobScheduler.schedule(builder.build())
            }
        }
    }

    @Inject
    lateinit var apiClient: ISparkPointUpdateCheckApi

    override fun onCreate() {
        super.onCreate()

        // TODO: Setup DI
        setupDI()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_CHECK_UPDATES -> checkAppUpdates(intent)
            else -> Timber.e("Hey develop, this $this is for checking version only!")
        }
    }

    // DI /////////////////////////////////////////////////////////////////////

    private fun setupDI() {
        val appComponent = UpdaterApp.appComponent
        appComponent.inject(this)
    }

    // Check Updates //////////////////////////////////////////////////////////

    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    private fun checkAppUpdates(
        intent: Intent
    ) {
        val packageNames = intent.getStringArrayExtra(IntentActions.PROP_APP_PACKAGE_NAMES) ?: throw IllegalArgumentException("Must provide a package name list")

        val updates = mutableListOf<AppUpdate>()
        packageNames.forEach { name ->
            try {
                val update = queryAppUpdate(name)
                updates.add(update)
            } catch (err: Throwable) {
                Timber.e(err)
            }
        }

        // Notify the updater to move on!
        val responseIntent = Intent().apply {
            if (updates.isNotEmpty()) {
                action = IntentActions.ACTION_UPDATES_FOUND
                putExtra(IntentActions.PROP_APP_UPDATES, updates.toTypedArray())
            } else {
                action = IntentActions.ACTION_UPDATES_NOT_FOUND
            }
        }
        broadcastManager.sendBroadcast(responseIntent)
    }

    private fun queryAppUpdate(
        packageName: String
    ): AppUpdate {
        Timber.v("[Check] Check update for \"$packageName\"")

        // TODO: Send different query parameters for SparkPoint
        // when (packageName) {
        //     PACKAGE_SPARK_POINT -> TODO()
        //     else -> TODO()
        // }

        val apiRequest = apiClient.getAppUpdate(
            packageName = packageName
        )
        val apiResponse = apiRequest.execute()

        // TODO: Convert API response to AppUpdate
        return if (apiResponse.isSuccessful) {
            val body = apiResponse.body() ?: throw NullPointerException("Couldn't get AppUpdate body")
            Timber.i("[Check] Got latest version, \"${body.versionName}\" for \"$packageName\"")

            // Close the connection to avoid leak!
            // apiResponse.raw().close()

            body
        } else {
            // TODO: Turn error body to our exception?
            throw HttpException(apiResponse)
        }
    }
}