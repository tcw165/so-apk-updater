package co.sodalabs.apkupdater.feature.heartbeat

import Packages
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
import android.os.SystemClock
import androidx.core.app.JobIntentService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.apkupdater.ISharedSettings
import co.sodalabs.apkupdater.ISystemProperties
import co.sodalabs.apkupdater.SharedSettingsProps
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.data.SystemProps
import co.sodalabs.apkupdater.feature.heartbeat.api.ISparkPointHeartBeatApi
import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatBody
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.HTTPResponseCode
import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import co.sodalabs.updaterengine.extension.benchmark
import co.sodalabs.updaterengine.extension.getPrettyDateNow
import timber.log.Timber
import javax.inject.Inject

private const val INITIAL_CHECK_DELAY_MILLIS = 1000L // 1 second

class HeartBeatJobIntentService : JobIntentService() {

    companion object {

        fun sendHeartBeatNow(
            context: Context
        ) {
            val intent = Intent(context, HeartBeatJobIntentService::class.java)
            intent.action = IntentActions.ACTION_SEND_HEART_BEAT_NOW
            enqueueWork(context, ComponentName(context, HeartBeatJobIntentService::class.java), UpdaterJobs.JOB_ID_HEART_BEAT, intent)
        }

        fun scheduleRecurringHeartBeat(
            context: Context,
            intervalMs: Long
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                Timber.v("[HeartBeat] (< 21) Schedule a recurring update, using AlarmManager")

                val intent = Intent(context, HeartBeatJobIntentService::class.java)
                intent.action = IntentActions.ACTION_SEND_HEART_BEAT_NOW

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                // TODO: Do we need to recover the scheduling on boot?
                alarmManager.cancel(pendingIntent)
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + INITIAL_CHECK_DELAY_MILLIS,
                    intervalMs,
                    pendingIntent
                )
            } else {
                Timber.v("[HeartBeat] (>= 21) Schedule a recurring update, using android-21 JobScheduler")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
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
    }

    @Inject
    lateinit var apiClient: ISparkPointHeartBeatApi
    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var systemProperties: ISystemProperties

    override fun onCreate() {
        super.onCreate()
        injectDependencies()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_SEND_HEART_BEAT_NOW -> sendHeartBeat()
            else -> throw IllegalArgumentException("Hey develop, HeartBeatJobIntentService is for checking version only!")
        }
    }

    // DI /////////////////////////////////////////////////////////////////////

    private fun injectDependencies() {
        val appComponent = UpdaterApp.appComponent
        appComponent.inject(this)
    }

    // Heart Beat /////////////////////////////////////////////////////////////

    /**
     * We use local broadcast to notify the async result.
     */
    private val broadcastManager by lazy { LocalBroadcastManager.getInstance(this) }

    private fun sendHeartBeat() {
        try {
            val deviceID = getDeviceID()
            val hardwareID = getHardwareID()
            val firmwareVersion = getFirmwareVersion()
            val sparkpointPlayerVersion = getSparkpointPlayerVersion()

            val provisioned = sharedSettings.isDeviceProvisioned()
            val userSetupComplete = sharedSettings.isUserSetupComplete()
            Timber.v("[HeartBeat] provisioned: $provisioned, user setup complete: $userSetupComplete")
            if (!provisioned || !userSetupComplete) {
                reportDeviceNotSetup(deviceID)
                return
            }

            val timeMs = benchmark {
                val apiBody = HeartBeatBody(
                    deviceID,
                    hardwareID,
                    firmwareVersion,
                    sparkpointPlayerVersion
                )
                val now = getPrettyDateNow()
                Timber.v("[HeartBeat] Health check at $now for device, API body:\n$apiBody")

                val apiRequest = apiClient.poke(apiBody)
                val apiResponse = apiRequest.execute()
                reportAPIResponse(apiResponse.code())
            }

            if (timeMs >= 15000) {
                Timber.e("Hey, heart-beat API call for device(ID: $deviceID) took $timeMs milliseconds!")
            }
        } catch (error: Throwable) {
            Timber.e(error)
            reportAPINoResponse(error)
        }
    }

    private fun getDeviceID(): String {
        return sharedSettings.getSecureString(SharedSettingsProps.DEVICE_ID) ?: ""
    }

    private fun getHardwareID(): String {
        return sharedSettings.getHardwareId()
    }

    private fun getFirmwareVersion(): String {
        return systemProperties.getString(SystemProps.FIRMWARE_VERSION_INCREMENTAL, "")
    }

    private fun getSparkpointPlayerVersion(): String {
        return try {
            val info = packageManager.getPackageInfo(Packages.SPARKPOINT_PACKAGE_NAME, PackageManager.GET_META_DATA)
            info.versionName
        } catch (error: PackageManager.NameNotFoundException) {
            // The package is not installed (or deleted)
            ""
        }
    }

    // Broadcast //////////////////////////////////////////////////////////////

    private fun generateHeartBeatIntent(
        responseCode: Int
    ): Intent {
        val intent = Intent(IntentActions.ACTION_SEND_HEART_BEAT_NOW)
        intent.putExtra(IntentActions.PROP_HTTP_RESPONSE_CODE, responseCode)
        return intent
    }

    private fun reportDeviceNotSetup(
        deviceID: String
    ) {
        val failureIntent = generateHeartBeatIntent(HTTPResponseCode.Unknown.code)
        failureIntent.putExtra(IntentActions.PROP_ERROR, DeviceNotSetupException(deviceID))
        broadcastManager.sendBroadcast(failureIntent)
    }

    private fun reportAPIResponse(
        code: Int
    ) {
        val successIntent = generateHeartBeatIntent(code)
        broadcastManager.sendBroadcast(successIntent)
    }

    private fun reportAPINoResponse(
        error: Throwable
    ) {
        val failureIntent = generateHeartBeatIntent(HTTPResponseCode.Unknown.code)
        failureIntent.putExtra(IntentActions.PROP_ERROR, error)
        broadcastManager.sendBroadcast(failureIntent)
    }
}