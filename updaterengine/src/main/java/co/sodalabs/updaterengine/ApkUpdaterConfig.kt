package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
data class ApkUpdaterConfig(
    val hostPackageName: String,
    val packageNames: List<String>,
    val checkInterval: Long,
    val heartBeatInterval: Long
) {

    init {
        if (checkInterval <= 0) {
            throw IllegalArgumentException("Interval must be greater than zero.")
        }
    }
}