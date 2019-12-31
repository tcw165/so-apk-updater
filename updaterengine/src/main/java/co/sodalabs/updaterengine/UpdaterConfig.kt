package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import java.io.File
import java.util.Calendar

/**
 * The updater engine configuration such as package names for update, check interval,
 * ... etc. The configuration is immutable. In other words, you'll need to restart
 * the process for the configuration change in your implementation.
 */
interface UpdaterConfig {
    val hostPackageName: String
    val packageNames: List<String>
    /**
     * Send the health check to the server every X milliseconds.
     */
    var heartbeatIntervalMillis: Long
    /**
     * Check the updates from the server every X milliseconds.
     */
    var checkIntervalMillis: Long
    /**
     * Boolean for beta or non-beta channel for the response from API.
     */
    val checkBetaAllowed: Boolean
    /**
     * The window for installing updates. There is a begin and end hour of the
     * day, the same as [Calendar.HOUR_OF_DAY].
     */
    var installWindow: IntRange
    /**
     * Install the update automatically and silently
     */
    var installSilently: Boolean
    /**
     * A debug function for install the downgrade version.
     */
    var installAllowDowngrade: Boolean
    /**
     * True to use a LRU cache for storing the downloaded files. False to indicate
     * the engine to wipe all the previous downloads whenever it starts new download.
     */
    var downloadUseCache: Boolean
    /**
     * The root directory for all the disk cache.
     */
    val baseDiskCacheDir: File
    /**
     * Used for caching the downloaded APK files in disk.
     */
    val updateDiskCache: DiskLruCache
}