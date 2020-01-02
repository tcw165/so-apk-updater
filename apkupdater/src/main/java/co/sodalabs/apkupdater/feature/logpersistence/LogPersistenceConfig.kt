package co.sodalabs.apkupdater.feature.logpersistence

import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.updaterengine.feature.logPersistence.ILogPersistenceConfig
import javax.inject.Inject

/**
 * A collection of items required by Log Persistence Worker and Scheduler
 */
class LogPersistenceConfig @Inject constructor() : ILogPersistenceConfig {
    override val tag: String = BuildConfig.APPLICATION_ID
    override val maxLogFileSizeInBytes: Long = BuildConfig.LOG_FILE_MAX_SIZE_IN_BYTES
    override val maxLogFieDurationInMillis: Long = BuildConfig.LOG_FILE_MAX_DURATION_IN_MILLIS
    override val repeatIntervalInMillis: Long = BuildConfig.LOG_REPEAT_INTERVAL_IN_MILLIS
    override val whitelist: List<String> = BuildConfig.LOGGING_WHITELIST.toList()
    override val maxLogLinesCount: Int = BuildConfig.LOG_MAX_LINES_COUNT
}