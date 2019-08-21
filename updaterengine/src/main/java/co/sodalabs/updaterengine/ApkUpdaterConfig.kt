package co.sodalabs.updaterengine

import androidx.annotation.Keep
import java.util.Calendar

/**
 * The updater engine configuration such as package names for update, check interval,
 * ... etc. The configuration is immutable. In other words, you'll need to restart
 * the process for the configuration change in your implementation.
 */
@Keep
data class ApkUpdaterConfig(
    val hostPackageName: String,
    val packageNames: List<String>,
    /**
     * Send the health check to the server every X milliseconds.
     */
    val heartbeatIntervalMillis: Long,
    /**
     * Check the updates from the server every X milliseconds.
     */
    val checkIntervalMillis: Long,
    /**
     * The window for installing updates. There is a begin and end hour of the
     * day, the same as [Calendar.HOUR_OF_DAY].
     */
    val installWindow: IntRange,
    /**
     * A debug function for install the downgrade version.
     */
    val installAllowDowngrade: Boolean,
    /**
     * True to use a LRU cache for storing the downloaded files. False to indicate
     * the engine to wipe all the previous downloads whenever it starts new download.
     */
    val downloadUseCache: Boolean
) {

    init {
        if (checkIntervalMillis <= 0) {
            throw IllegalArgumentException("Interval must be greater than zero.")
        }
    }
}