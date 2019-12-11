package co.sodalabs.updaterengine.feature.logPersistence

interface ILogPersistenceConfig {
    val tag: String
    val maxLogFileSizeInBytes: Long
    val maxLogFieDurationInMillis: Long
    val repeatIntervalInMillis: Long
    val whitelist: List<String>
    val maxLogLinesCount: Int
}