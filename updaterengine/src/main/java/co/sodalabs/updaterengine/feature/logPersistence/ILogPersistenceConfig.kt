package co.sodalabs.updaterengine.feature.logPersistence

interface ILogPersistenceConfig {
    val tag: String
    val maxLogFileSize: Int
    val maxLogFieDuration: Long
    val repeatInterval: Long
}