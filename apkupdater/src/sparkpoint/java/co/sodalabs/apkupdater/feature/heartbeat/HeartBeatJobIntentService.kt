package co.sodalabs.apkupdater.feature.heartbeat

import Packages
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
import co.sodalabs.apkupdater.feature.heartbeat.api.ISparkPointHeartBeatApi
import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatBody
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.UpdaterJobs
import co.sodalabs.updaterengine.data.HTTPResponseCode
import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import co.sodalabs.updaterengine.extension.benchmark
import co.sodalabs.updaterengine.extension.getPrettyDateNow
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import dagger.android.AndroidInjection
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
    lateinit var appPreference: IAppPreference
    @Inject
    lateinit var packageVersionProvider: IPackageVersionProvider
    @Inject
    lateinit var sharedSettings: ISharedSettings
    @Inject
    lateinit var systemProperties: ISystemProperties
    @Inject
    lateinit var updaterStateTracker: IUpdaterStateTracker

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onHandleWork(intent: Intent) {
        when (intent.action) {
            IntentActions.ACTION_SEND_HEART_BEAT_NOW -> sendHeartBeat()
            else -> throw IllegalArgumentException("Hey develop, HeartBeatJobIntentService is for checking version only!")
        }
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
            val apkUpdaterVersion = getApkUpdaterVersion()

            // Pull the updater state.
            // Note: There was a race condition in between getting updater state
            // and the metadata.
            val (state, stateMetadata) = updaterStateTracker.snapshotStateWithMetadata()

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
                    sparkpointPlayerVersion,
                    apkUpdaterVersion,
                    state.name,
                    stateMetadata
                )
                val now = getPrettyDateNow()
                Timber.v("[HeartBeat] Health check at $now for device, API body:\n$apiBody")

                val apiRequest = apiClient.poke(apiBody)
                val apiResponse = apiRequest.execute()
                reportAPIResponse(apiResponse.code())
            }

            if (timeMs >= 15000) {
                Timber.w("Hey, heart-beat API call for device(ID: $deviceID) took $timeMs milliseconds!")
            }
        } catch (error: Throwable) {
            reportAPINoResponse(error)
        }
    }

    private fun getDeviceID(): String {
        return sharedSettings.getDeviceId()
    }

    private fun getHardwareID(): String {
        return sharedSettings.getHardwareId()
    }

    private fun getFirmwareVersion(): String {
        return systemProperties.getFirmwareVersion()
    }

    private fun getSparkpointPlayerVersion(): String {
        return packageVersionProvider.getPackageVersion(Packages.SPARKPOINT_PACKAGE_NAME)
    }

    private fun getApkUpdaterVersion(): String {
        return packageVersionProvider.getPackageVersion(this.packageName)
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