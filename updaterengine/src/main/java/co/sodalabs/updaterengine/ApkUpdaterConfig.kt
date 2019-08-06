package co.sodalabs.updaterengine

import androidx.annotation.Keep
import java.util.Calendar

@Keep
data class ApkUpdaterConfig(
    val hostPackageName: String,
    val packageNames: List<String>,
    val checkIntervalMs: Long,
    val heartBeatIntervalMs: Long,
    /**
     * The window for installing updates. There is a begin and end hour of the
     * day, the same as [Calendar.HOUR_OF_DAY].
     */
    val installWindow: IntRange,
    val installAllowDowngrade: Boolean
) {

    init {
        if (checkIntervalMs <= 0) {
            throw IllegalArgumentException("Interval must be greater than zero.")
        }
    }
}