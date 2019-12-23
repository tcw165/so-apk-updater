package co.sodalabs.apkupdater.feature.remoteConfig

import android.app.AlarmManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import co.sodalabs.updaterengine.di.WorkerInjection
import timber.log.Timber

private const val PREFIX = "remote_config"

internal const val PARAM_TIMEZONE_CITY_ID = "$PREFIX.timezone_city_id"

class RemoteConfigSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val alarmManager by lazy { context.getSystemService(Context.ALARM_SERVICE) as AlarmManager }

    override fun doWork(): Result {
        WorkerInjection.inject(this)

        return try {
            applyTimezone()

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
}