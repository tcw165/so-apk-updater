package co.sodalabs.apkupdater.feature.remoteConfig

import android.app.AlarmManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.di.WorkerInjection
import timber.log.Timber
import javax.inject.Inject

private const val PREFIX = "remote_config"

internal const val PARAM_TIMEZONE_CITY_ID = "$PREFIX.timezone_city_id"
internal const val PARAM_INSTALL_WINDOW = "$PREFIX.install_window"
internal const val PARAM_ALLOW_DOWNGRADE = "$PREFIX.allow_downgrade"
internal const val PARAM_CHECK_INTERVAL = "$PREFIX.check_interval"
internal const val PARAM_USE_DISK_CACHE = "$PREFIX.param_use_disk_cache"
internal const val PARAM_FORCE_FULL_FIRMWARE_UPDATE = "$PREFIX.param_force_full_firmware_update"

class RemoteConfigSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    @Inject
    lateinit var appPreference: IAppPreference

    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    override fun doWork(): Result {
        WorkerInjection.inject(this)

        return try {
            applyTimezone()
            applyInstallWindow()
            applyDowngradeFlag()
            applyCheckInterval()
            applyDiskCacheFlag()
            applyFullFirmwareUpdateFlag()

            Result.success()
        } catch (error: Throwable) {
            Result.failure()
        }
    }

    private fun applyTimezone() {
        val timezoneCityOpt = inputData.getString(PARAM_TIMEZONE_CITY_ID)
        timezoneCityOpt?.let { timezoneCity ->
            Timber.v("[RemoteConfig] Change timezone city ID to '$timezoneCity'")
            alarmManager.setTimeZone(timezoneCity)
        }
    }

    private fun applyInstallWindow() {
        val installWindowOpt = inputData.getString(PARAM_INSTALL_WINDOW)
            ?.split("-")
            ?.map { it.toInt() }
            ?: return
        require(installWindowOpt.size == 2) { "Hey developer, the install window array size should be exactly two!" }

        val currentWindowStart = appPreference.getInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
        val currentWindowEnd = appPreference.getInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
        val (installWindowStart, installWindowEnd) = installWindowOpt

        if (currentWindowStart != installWindowStart) {
            Timber.v("[RemoteConfig] Changing install window start from '$currentWindowStart' to '$installWindowStart'")
            appPreference.putInt(PreferenceProps.INSTALL_HOUR_BEGIN, installWindowStart)
        }
        if (currentWindowEnd != installWindowEnd) {
            Timber.v("[RemoteConfig] Changing install window end from '$currentWindowEnd' to '$installWindowEnd'")
            appPreference.putInt(PreferenceProps.INSTALL_HOUR_END, installWindowEnd)
        }
    }

    private fun applyDowngradeFlag() {
        val newDowngradeFlag = inputData.getBoolean(PARAM_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)
        val currentDowngradeFlag = appPreference.getBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)

        if (currentDowngradeFlag != newDowngradeFlag) {
            Timber.v("[RemoteConfig] Changing allow downgrade flag from '$currentDowngradeFlag' to '$newDowngradeFlag'")
            appPreference.putBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, newDowngradeFlag)
        }
    }

    private fun applyDiskCacheFlag() {
        val newDiskCacheFlag = inputData.getBoolean(PARAM_USE_DISK_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
        val currentDiskCacheFlag = appPreference.getBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)

        if (currentDiskCacheFlag != newDiskCacheFlag) {
            Timber.v("[RemoteConfig] Changing disk cache flag from '$currentDiskCacheFlag' to '$newDiskCacheFlag'")
            appPreference.putBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, newDiskCacheFlag)
        }
    }

    private fun applyFullFirmwareUpdateFlag() {
        val newFirmwareUpdateFlag = inputData.getBoolean(PARAM_FORCE_FULL_FIRMWARE_UPDATE, false)
        val currentFirmwareUpdateFlag = appPreference.getBoolean(PreferenceProps.MOCK_USER_SETUP_INCOMPLETE, false)

        if (currentFirmwareUpdateFlag != newFirmwareUpdateFlag) {
            Timber.v("[RemoteConfig] Changing force full firmware update flag from '$currentFirmwareUpdateFlag' to '$newFirmwareUpdateFlag'")
            appPreference.putBoolean(PreferenceProps.MOCK_USER_SETUP_INCOMPLETE, newFirmwareUpdateFlag)
        }
    }

    private fun applyCheckInterval() {
        val newCheckInterval = inputData.getLong(PARAM_CHECK_INTERVAL, BuildConfig.CHECK_INTERVAL_SECONDS)
        val currentCheckInterval = appPreference.getLong(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)

        if (currentCheckInterval != newCheckInterval) {
            Timber.v("[RemoteConfig] Changing check interval from '$currentCheckInterval' to '$newCheckInterval'")
            appPreference.putLong(PreferenceProps.CHECK_INTERVAL_SECONDS, newCheckInterval)
        }
    }
}