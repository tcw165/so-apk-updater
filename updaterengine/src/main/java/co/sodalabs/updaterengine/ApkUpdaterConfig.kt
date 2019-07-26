package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
data class ApkUpdaterConfig(
    val hostPackageName: String,
    val packageNames: List<String>,
    val checkIntervalMs: Long,
    val heartBeatIntervalMs: Long
) {

    init {
        if (checkIntervalMs <= 0) {
            throw IllegalArgumentException("Interval must be greater than zero.")
        }
    }
}