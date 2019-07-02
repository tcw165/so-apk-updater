package co.sodalabs.updaterengine

import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

@Keep
data class ApkUpdaterConfig(
    val hostPackageName: String,
    val packageNames: List<String> = listOf(hostPackageName),
    val interval: Long = TimeUnit.DAYS.toMillis(1)
) {

    init {
        if (interval <= 0) {
            throw IllegalArgumentException("Interval must be greater than zero.")
        }
    }
}