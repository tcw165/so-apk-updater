package co.sodalabs.apkupdater.feature.logpersistence

import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.updaterengine.feature.logPersistence.ILogPersistenceConfig
import javax.inject.Inject

/**
 * A collection of items required by Log Persistence Worker and Scheduler
 */
class LogPersistenceConfig @Inject constructor() : ILogPersistenceConfig {
    override val tag: String = BuildConfig.APPLICATION_ID
    override val maxLogFileSizeInBytes: Long = BuildConfig.MAX_LOG_FILE_SIZE_IN_BYTES
    override val maxLogFieDurationInMillis: Long = BuildConfig.MAX_FILE_DURATION_IN_MILLIS
    override val repeatIntervalInMillis: Long = BuildConfig.REPEAT_INTERVAL_IN_MILLIS
    override val whitelist: List<String> = BuildConfig.LOGGING_WHITELIST.toList()
    override val maxLogLinesCount: Int = BuildConfig.MAX_LOG_LINES_COUNT
}