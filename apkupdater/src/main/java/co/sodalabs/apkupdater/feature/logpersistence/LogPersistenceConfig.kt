package co.sodalabs.apkupdater.feature.logpersistence

import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.updaterengine.feature.logPersistence.ILogPersistenceConfig
import javax.inject.Inject

/**
 * A collection of items required by Log Persistence Worker and Scheduler
 */
class LogPersistenceConfig @Inject constructor() : ILogPersistenceConfig {
    override val tag: String = BuildConfig.APPLICATION_ID
    override val maxLogFileSize: Int = BuildConfig.MAX_LOG_FILE_SIZE
    override val maxLogFieDuration: Long = BuildConfig.MAX_FILE_DURATION
    override val repeatInterval: Long = BuildConfig.REPEAT_INTERVAL
}