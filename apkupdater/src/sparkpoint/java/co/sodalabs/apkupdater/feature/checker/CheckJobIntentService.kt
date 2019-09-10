package co.sodalabs.apkupdater.feature.checker

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import androidx.core.app.JobIntentService
import co.sodalabs.apkupdater.ISharedSettings
import co.sodalabs.apkupdater.SharedSettingsProps
import co.sodalabs.apkupdater.feature.checker.api.ISparkPointUpdateCheckApi
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterJobs.JOB_ID_CHECK_UPDATES
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.extension.getIndicesToRemove
import dagger.android.AndroidInjection
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

class CheckJobIntentService : JobIntentService() {

    companion object {

        fun checkUpdatesNow(
            context: Context,
            packageNames: Array<String>
        ) {
            val intent = Intent(context, CheckJobIntentService::class.java)
            intent.action = IntentActions.ACTION_CHECK_UPDATES
            intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)
            enqueueWork(
                context,
                ComponentName(context, CheckJobIntentService::class.java),
                JOB_ID_CHECK_UPDATES,
                intent
            )
        }

        fun scheduleNextCheck(
            context: Context,
            packageNames: Array<String>,
            delayMillis: Long
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Check] (< 21) Schedule a pending check, using AlarmManager")

                val intent = Intent(context, CheckJobIntentService::class.java)
                intent.action = IntentActions.ACTION_CHECK_UPDATES
                intent.putExtra(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                alarmManager.cancel(pendingIntent)
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    delayMillis,
                    pendingIntent
                )
            } else {
                Timber.v("[Check] (>= 21) Schedule a pending check, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, CheckJobService::class.java)
                val bundle = PersistableBundle()
                bundle.putStringArray(IntentActions.PROP_APP_PACKAGE_NAMES, packageNames)

                val builder = JobInfo.Builder(JOB_ID_CHECK_UPDATES, componentName)
                    .setRequiresDeviceIdle(false)
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

        fun cancelRecurringUpdateCheck(
            context: Context
        ) {
            val intent = Intent(context, CheckJobIntentService::class.java)
            intent.action = IntentActions.ACTION_CHECK_UPDATES

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[Check] (< 21) Cancel the pending jobs using AlarmManager")
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
            } else {
                Timber.v("[Check] (>= 21) Cancel the pending jobs using android-21 JobScheduler")
                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                jobScheduler.cancel(JOB_ID_CHECK_UPDATES)
            }

            // Stop the service immediately
            Timber.v("[Check] Stop the service now!")
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var updaterConfig: UpdaterConfig
    @Inject
    lateinit var apiClient: ISparkPointUpdateCheckApi
    @Inject
    lateinit var sharedSettings: ISharedSettings

    override fun onCreate() {
        Timber.v("[Check] Check Service is online")
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onDestroy() {
        Timber.v("[Check] Check Service is offline")
        super.onDestroy()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_CHECK_UPDATES -> checkAppUpdates(intent)
            else -> Timber.e("Hey develop, this $this is for checking version only!")
        }
    }

    // Check Updates //////////////////////////////////////////////////////////

    private fun checkAppUpdates(
        intent: Intent
    ) {
        val packageNames = intent.getStringArrayExtra(IntentActions.PROP_APP_PACKAGE_NAMES)
            ?: throw IllegalArgumentException("Must provide a package name list")

        val updates = mutableListOf<AppUpdate>()
        var updatesError: Throwable? = null
        // Query the updates.
        packageNames.forEach { name ->
            try {
                val update = queryAppUpdate(name)
                updates.add(update)
            } catch (err: Throwable) {
                updatesError = err
            }
        }
        // Filter the invalid updates.
        val trimmedUpdates = updates.trimByVersionCheck(
            packageManager,
            allowDowngrade = updaterConfig.installAllowDowngrade
        )

        // Notify the updater to move on!
        UpdaterService.notifyUpdateCheckComplete(this, trimmedUpdates, updatesError)
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

        val request = apiClient.getAppUpdate(
            packageName = packageName,
            deviceId = getDeviceID()
        )
        val response = request.execute()

        return if (response.isSuccessful) {
            val body = response.body() ?: throw NullPointerException("Couldn't get AppUpdate body")
            val bodyClone = body.copy()

            // Close the connection to avoid leak!
            // response.raw().body()?.close()

            bodyClone
        } else {
            throw HttpException(response)
        }
    }

    private fun List<AppUpdate>.trimByVersionCheck(
        packageManager: PackageManager,
        allowDowngrade: Boolean
    ): List<AppUpdate> {
        val originalUpdates = this
        val indicesToRemove = this.getIndicesToRemove(packageManager, allowDowngrade)
        val updatesToRemove = indicesToRemove.map { i -> originalUpdates[i] }

        val trimmedList = this.toMutableList()
        trimmedList.removeAll(updatesToRemove)

        return trimmedList
    }

    private fun getDeviceID(): String {
        return sharedSettings.getSecureString(SharedSettingsProps.DEVICE_ID) ?: ""
    }
}