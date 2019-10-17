package co.sodalabs.apkupdater

import android.content.Context
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.extension.mbToBytes
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import co.sodalabs.updaterengine.utils.StorageUtils
import java.io.File
import javax.inject.Inject

private const val CACHE_APK_DIR = "apks"
private const val CACHE_APK_SIZE_MB = 1024
private const val CACHE_FIRMWARE_DIR = "system_image"
private const val CACHE_FIRMWARE_SIZE_MB = 1024
private const val CACHE_DOWNLOADED_UPDATE_DIR = "downloaded_update"
/**
 * Version history:
 * 1 - The initial cache with the nested key hierarchy.
 * 2 - The flat key hierarchy.
 */
private const val CACHE_JOURNAL_VERSION = 2
private const val CACHE_UPDATE_RECORDS_SIZE_MB = 10

private const val DAY_MIN = 0
private const val DAY_MAX = 24

class AndroidUpdaterConfig @Inject constructor(
    private val context: Context, // Application context is fine
    private val appPreference: IAppPreference
) : UpdaterConfig {

    init {
        require(checkIntervalMillis > 0) { "Interval must be greater than zero." }
    }

    override val hostPackageName: String by lazy { context.packageName }
    override val packageNames: List<String> = listOf(*BuildUtils.PACKAGES_TO_CHECK)

    override var installSilently: Boolean
        get() = appPreference.getBoolean(PreferenceProps.INSTALL_SILENTLY, BuildConfig.INSTALL_SILENTLY)
        set(value) {
            appPreference.putBoolean(PreferenceProps.INSTALL_SILENTLY, value)
        }

    override var heartbeatIntervalMillis: Long
        get() = 1000L * appPreference.getLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
        set(value) {
            require(value < Intervals.SAMPLE_INTERVAL_NORMAL) { "The given interval, $value, is too small, which should be greater than ${Intervals.SAMPLE_INTERVAL_NORMAL}" }
            appPreference.putLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, value)
        }

    override var checkIntervalMillis: Long
        get() = 1000L * appPreference.getLong(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)
        set(value) {
            require(value < Intervals.SAMPLE_INTERVAL_NORMAL) { "The given interval, $value, is too small, which should be greater than ${Intervals.SAMPLE_INTERVAL_NORMAL}" }
            appPreference.putLong(PreferenceProps.CHECK_INTERVAL_SECONDS, value)
        }

    @Suppress("ReplaceRangeStartEndInclusiveWithFirstLast")
    override var installWindow: IntRange
        get() {
            val begin = appPreference.getInt(PreferenceProps.INSTALL_HOUR_BEGIN, BuildConfig.INSTALL_HOUR_BEGIN)
            val end = appPreference.getInt(PreferenceProps.INSTALL_HOUR_END, BuildConfig.INSTALL_HOUR_END)
            return IntRange(begin, end)
        }
        set(value) {
            // Constrain the value within 0 until 24
            require(value.start in DAY_MIN..DAY_MAX) { "Start (${value.first}) is not a valid hour of a day" }
            require(value.endInclusive in DAY_MIN..DAY_MAX) { "End (${value.first}) is not a valid hour of a day" }

            val begin = value.start % DAY_MAX
            val end = value.endInclusive % DAY_MAX
            appPreference.putInt(PreferenceProps.INSTALL_HOUR_BEGIN, begin)
            appPreference.putInt(PreferenceProps.INSTALL_HOUR_END, end)
        }

    override var installAllowDowngrade: Boolean
        get() = appPreference.getBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, BuildConfig.INSTALL_ALLOW_DOWNGRADE)
        set(value) {
            appPreference.putBoolean(PreferenceProps.INSTALL_ALLOW_DOWNGRADE, value)
        }

    override var downloadUseCache: Boolean
        get() = appPreference.getBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, BuildConfig.DOWNLOAD_USE_CACHE)
        set(value) {
            appPreference.putBoolean(PreferenceProps.DOWNLOAD_USE_CACHE, value)
        }

    override val apkDiskCache: DiskLruCache by lazy {
        // The cache dir would be "/storage/emulated/legacy/co.sodalabs.apkupdater/${CACHE_APK_DIR}/"
        DiskLruCache(
            File(StorageUtils.getCacheDirectory(context, true), CACHE_APK_DIR),
            CACHE_JOURNAL_VERSION,
            CACHE_APK_SIZE_MB.mbToBytes()
        )
    }

    override val firmwareDiskCache: DiskLruCache by lazy {
        // The cache dir would be "/storage/emulated/legacy/co.sodalabs.apkupdater/${CACHE_FIRMWARE_DIR}/"
        DiskLruCache(
            File(StorageUtils.getCacheDirectory(context, true), CACHE_FIRMWARE_DIR),
            CACHE_JOURNAL_VERSION,
            CACHE_FIRMWARE_SIZE_MB.mbToBytes()
        )
    }

    override val downloadedUpdateDiskCache: DiskLruCache by lazy {
        // The cache dir would be "/storage/emulated/legacy/co.sodalabs.apkupdater/${CACHE_DOWNLOADED_UPDATE_DIR}/"
        DiskLruCache(
            File(StorageUtils.getCacheDirectory(context, true), CACHE_DOWNLOADED_UPDATE_DIR),
            CACHE_JOURNAL_VERSION,
            CACHE_UPDATE_RECORDS_SIZE_MB.mbToBytes()
        )
    }
}