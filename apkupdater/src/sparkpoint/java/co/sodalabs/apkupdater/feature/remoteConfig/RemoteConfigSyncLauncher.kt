package co.sodalabs.apkupdater.feature.remoteConfig

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import timber.log.Timber
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val UNIQUE_WORK_NAME_SYNC_REMOTE_CONFIG = "sync_remote_config"

private const val IMMEDIATELY_MILLIS = 0L

class RemoteConfigSyncLauncher @Inject constructor(
    private val context: Context,
    private val timezoneMapper: ITimezoneMapper
) : IRemoteConfigSyncLauncher {

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun applyRemoteConfigNow(
        config: RemoteConfig
    ) {
        val dataPairs = mutableMapOf<String, Any>()
        dataPairs.addIfNotNull(addTimezoneFromConfig(config))
        dataPairs.addIfNotNull(addInstallWindowFromConfig(config))
        dataPairs.addIfNotNull(addDowngradeFlagFromConfig(config))
        dataPairs.addIfNotNull(addCheckIntervalFromConfig(config))
        dataPairs.addIfNotNull(addDiskCacheFlagFromConfig(config))
        dataPairs.addIfNotNull(addFullFirmwareUpdateFlagFromConfig(config))
        // Following are the fields for one-shot action from the server.
        dataPairs.addIfNotNull(addRebootFlagFromConfig(config))
        if (dataPairs.isEmpty()) {
            // Do nothing since remote config is exactly the same with the local
            // config.
            return
        }

        Timber.v("[RemoteConfig] Schedule work for synchronizing the remote config...")

        val requestData = dataPairs.toRequestData()
        val requestConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(0L, TimeUnit.MILLISECONDS)
            // Note: We need the internet to do some handshake with the server.
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequest
            .Builder(RemoteConfigSyncWorker::class.java)
            .setInitialDelay(IMMEDIATELY_MILLIS, TimeUnit.MILLISECONDS)
            .setConstraints(requestConstraints)
            .setInputData(requestData)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME_SYNC_REMOTE_CONFIG,
            // Since the work is persistable and we'll schedule new one on boot,
            // it's always safe to override the work!
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun MutableMap<String, Any>.addIfNotNull(
        pairOpt: Pair<String, Any>?
    ) {
        pairOpt?.let { (key, value) -> this[key] = value }
    }

    private fun Map<String, Any>.toRequestData(): Data {
        val builder = Data.Builder()

        this.entries.forEach { (key, value) ->
            when (value) {
                is String -> builder.putString(key, value)
                is Boolean -> builder.putBoolean(key, value)
                is Int -> builder.putInt(key, value)
                is Long -> builder.putLong(key, value)
                else -> throw IllegalArgumentException("[RemoteConfig] Unsupported value type, $value")
            }
        }

        return builder.build()
    }

    // Time Related ///////////////////////////////////////////////////////////

    private fun addTimezoneFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val remoteTimezone = config.timezone ?: return null
        val currentTimezone = TimeZone.getDefault()
        val currentCity = currentTimezone.id
        val newCity = timezoneMapper.extractTimezoneCity(remoteTimezone)

        return if (currentCity != newCity && newCity != null) {
            Timber.v("[RemoteConfig] Ready to change timezone city ID from '$currentCity' to '$newCity'")
            PARAM_TIMEZONE_CITY_ID to newCity
        } else {
            null
        }
    }

    private fun addCheckIntervalFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val checkInterval = config.updateCheckInterval ?: return null
        return PARAM_CHECK_INTERVAL to checkInterval
    }

    // Install Related ////////////////////////////////////////////////////////

    private fun addInstallWindowFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val remoteWindowStart: Int = config.installWindowStart ?: return null
        val remoteWindowEnd: Int = config.installWindowEnd ?: return null
        return PARAM_INSTALL_WINDOW to "$remoteWindowStart-$remoteWindowEnd"
    }

    private fun addDowngradeFlagFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val allowDowngrade = config.allowDowngradeApp ?: return null
        return PARAM_ALLOW_DOWNGRADE to allowDowngrade
    }

    private fun addDiskCacheFlagFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val useDiskCache = config.downloadUsingDiskCache ?: return null
        return PARAM_USE_DISK_CACHE to useDiskCache
    }

    private fun addFullFirmwareUpdateFlagFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val forceFullFirmwareUpdate = config.forceFullFirmwareUpdate ?: return null
        return PARAM_FORCE_FULL_FIRMWARE_UPDATE to forceFullFirmwareUpdate
    }

    // Maintenance Related ////////////////////////////////////////////////////

    private fun addRebootFlagFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val forceFullFirmwareUpdate = config.toReboot ?: return null
        return PARAM_TO_REBOOT to forceFullFirmwareUpdate
    }

    // Section for the near future.
}