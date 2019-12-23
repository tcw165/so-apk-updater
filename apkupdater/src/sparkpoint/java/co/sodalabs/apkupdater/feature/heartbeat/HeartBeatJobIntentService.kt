package co.sodalabs.apkupdater.feature.heartbeat

import Packages
import android.content.Intent
import androidx.core.app.JobIntentService
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import co.sodalabs.apkupdater.feature.heartbeat.api.ISparkPointHeartBeatApi
import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatBody
import co.sodalabs.apkupdater.feature.remoteConfig.IRemoteConfigSyncLauncher
import co.sodalabs.apkupdater.feature.remoteConfig.RemoteConfig
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.ITimeUtil
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.PreferenceProps.HEARTBEAT_VERBAL_RESULT
import co.sodalabs.updaterengine.data.HTTPResponseCode
import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import co.sodalabs.updaterengine.extension.benchmark
import co.sodalabs.updaterengine.feature.statemachine.IUpdaterStateTracker
import dagger.android.AndroidInjection
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

class HeartBeatJobIntentService : JobIntentService() {

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
    @Inject
    lateinit var timeUtil: ITimeUtil
    // Note: The reason that heartbeat has to do with the remote config is this
    // implementation is low cost and viable before we scale up.
    @Inject
    lateinit var remoteConfigSyncLauncher: IRemoteConfigSyncLauncher

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
        val now = timeUtil.systemZonedNow().toString()

        try {
            val deviceID = sharedSettings.getDeviceId()
            val hardwareID = sharedSettings.getHardwareId()
            val firmwareVersion = systemProperties.getFirmwareVersion()
            val sparkpointPlayerVersion = packageVersionProvider.getPackageVersion(Packages.SPARKPOINT_PACKAGE_NAME)
            val apkUpdaterVersion = packageVersionProvider.getPackageVersion(this.packageName)

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
                Timber.v("[HeartBeat] Health check at $now for device, API body:\n$apiBody")

                val apiRequest = apiClient.poke(apiBody)
                val apiResponse = apiRequest.execute()

                apiResponse.body()?.let { response ->
                    // Note: The reason that heartbeat has to do with the remote
                    // config is this implementation is low cost and viable before
                    // we scale up.
                    scheduleApplyingRemoteConfig(response.remoteConfig)
                }

                reportAPIResponse(now, apiResponse.code())
            }

            if (timeMs >= 15000) {
                Timber.w("Hey, heart-beat API call for device(ID: $deviceID) took $timeMs milliseconds!")
            }
        } catch (error: Throwable) {
            reportAPINoResponse(now, error)
        }
    }

    // Remote Config //////////////////////////////////////////////////////////

    private fun scheduleApplyingRemoteConfig(
        remoteConfig: RemoteConfig
    ) {
        remoteConfigSyncLauncher.applyRemoteConfigNow(remoteConfig)
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
        zonedNow: String,
        code: Int
    ) {
        val successIntent = generateHeartBeatIntent(code)

        // Persist the result in shared-preference so that UI could get this
        // information at any moment.
        appPreference.putString(HEARTBEAT_VERBAL_RESULT, "Heartbeat was sent at $zonedNow (code: $code)")

        broadcastManager.sendBroadcast(successIntent)
    }

    private fun reportAPINoResponse(
        zonedNow: String,
        error: Throwable
    ) {
        if (error is DeviceNotSetupException ||
            error is SocketTimeoutException ||
            error is UnknownHostException) {
            Timber.w(error)
        } else {
            Timber.e(error)
        }

        // Persist the result in shared-preference so that UI could get this
        // information at any moment.
        appPreference.putString(HEARTBEAT_VERBAL_RESULT, "Heartbeat was sent at $zonedNow (error: $error)")

        val failureIntent = generateHeartBeatIntent(HTTPResponseCode.Unknown.code)
        failureIntent.putExtra(IntentActions.PROP_ERROR, error)
        broadcastManager.sendBroadcast(failureIntent)
    }
}