package co.sodalabs.apkupdater.feature.remoteConfig

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
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
        if (dataPairs.isEmpty()) {
            // Do nothing since remote config is exactly the same with the local
            // config.
            return
        }

        Timber.v("[RemoteConfig] Schedule work for synchronizing the remote config...")

        val requestData = dataPairs.toRequestData()
        val requestConstraints = Constraints.Builder()
            .setTriggerContentMaxDelay(0L, TimeUnit.MILLISECONDS)
            // Temporarily disable the network constraints since we only apply
            // config without network at this moment.
            // .setRequiredNetworkType(NetworkType.CONNECTED)
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
                else -> Timber.w("[RemoteConfig] Unsupported value type, $value")
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

    private fun addInstallWindowFromConfig(
        config: RemoteConfig
    ): Pair<String, Any>? {
        val remoteWindowStart: Int = config.installWindowStart ?: return null
        val remoteWindowEnd: Int = config.installWindowEnd ?: return null
        return PARAM_INSTALL_WINDOW to "$remoteWindowStart-$remoteWindowEnd"
    }

    // Install Related ////////////////////////////////////////////////////////

    // Section for the near future.
}