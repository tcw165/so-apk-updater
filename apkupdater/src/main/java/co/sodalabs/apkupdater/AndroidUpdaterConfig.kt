package co.sodalabs.apkupdater

import android.content.Context
import android.os.StatFs
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.extension.mbToBytes
import co.sodalabs.updaterengine.feature.lrucache.DiskLruCache
import co.sodalabs.updaterengine.utils.StorageUtils
import java.io.File
import javax.inject.Inject
import kotlin.math.max

private const val CACHE_UPDATE_DIR = "updates"
private const val CACHE_UPDATE_SIZE_MB = 2048
@Deprecated("Soon be replaced by shared preference")
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

private const val MIN_CHECK_INTERVAL_MILLIS = 2 * 60 * 1000L // 2 minutes
private const val MIN_HEARTBEAT_INTERVAL_MILLIS = 15 * 1000L // 15 seconds

class AndroidUpdaterConfig @Inject constructor(
    private val context: Context, // Application context is fine
    private val appPreference: IAppPreference
) : UpdaterConfig {

    init {
        require(checkIntervalMillis > 0) { "Interval must be greater than zero." }
    }

    override val hostPackageName: String by lazy { context.packageName }
    override val packageNames: List<String> = listOf(*BuildConfig.PACKAGES_TO_CHECK)

    override var installSilently: Boolean
        get() = appPreference.getBoolean(PreferenceProps.INSTALL_SILENTLY, BuildConfig.INSTALL_SILENTLY)
        set(value) {
            appPreference.putBoolean(PreferenceProps.INSTALL_SILENTLY, value)
        }

    override var heartbeatIntervalMillis: Long
        get() {
            val rawValue = 1000L * appPreference.getLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, BuildConfig.HEARTBEAT_INTERVAL_SECONDS)
            val validValue = ensureMinimumHeartbeatInterval(rawValue)
            if (rawValue != validValue) {
                appPreference.putLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, validValue / 1000L)
            }
            return validValue
        }
        // Currently, the setter is skipped because the [SettingsFragment] directly writes to the underlying SharedPrefs.
        // We will keep this here for future consumers of this api.
        set(value) {
            require(value >= MIN_HEARTBEAT_INTERVAL_MILLIS) { "The given interval, $value, is too small, which should be greater than $MIN_HEARTBEAT_INTERVAL_MILLIS" }
            appPreference.putLong(PreferenceProps.HEARTBEAT_INTERVAL_SECONDS, value / 1000L)
        }

    override var checkIntervalMillis: Long
        get() {
            val rawValue = 1000L * appPreference.getLong(PreferenceProps.CHECK_INTERVAL_SECONDS, BuildConfig.CHECK_INTERVAL_SECONDS)
            val validValue = ensureMinimumCheckInterval(rawValue)
            if (rawValue != validValue) {
                appPreference.putLong(PreferenceProps.CHECK_INTERVAL_SECONDS, validValue / 1000L)
            }
            return validValue
        }
        // Currently, the setter is skipped because the [SettingsFragment] directly writes to the underlying SharedPrefs.
        // We will keep this here for future consumers of this api.
        set(value) {
            require(value >= MIN_CHECK_INTERVAL_MILLIS) { "The given interval, $value, is too small, which should be greater than $MIN_CHECK_INTERVAL_MILLIS" }
            appPreference.putLong(PreferenceProps.CHECK_INTERVAL_SECONDS, value / 1000L)
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
    override val checkBetaAllowed: Boolean
        get() {
            val channelBeta = BuildConfig.UPDATE_CHANNELS[0]
            val channelStable = BuildConfig.UPDATE_CHANNELS[1]
            val channel = appPreference.getString(PreferenceProps.API_UPDATE_CHANNEL, channelStable)
            return channel == channelBeta
        }

    override val baseDiskCacheDir: File by lazy { StorageUtils.getCacheDirectory(context, true) }

    override val updateDiskCache: DiskLruCache by lazy {
        // The cache dir would be "/storage/emulated/legacy/co.sodalabs.apkupdater/${CACHE_APK_DIR}/"
        DiskLruCache(
            File(baseDiskCacheDir, CACHE_UPDATE_DIR),
            CACHE_JOURNAL_VERSION,
            CACHE_UPDATE_SIZE_MB.mbToBytes()
        )
    }

    private fun getAvailableSpaceInSDCard(): Long {
        val dir = StorageUtils.getCacheDirectory(context, true)
        return StatFs(dir.path).availableBytes
    }

    /**
     * The heartbeat interval is currently stored in SharedPrefs.
     * Because of this, anyone could skip the setter and write
     * an invalid value. Therefore, we must also enforce the
     * minimum in the getter.
     */
    private fun ensureMinimumHeartbeatInterval(
        rawMillis: Long
    ): Long {
        return max(rawMillis, MIN_HEARTBEAT_INTERVAL_MILLIS)
    }

    /**
     * The check interval is currently stored in SharedPrefs.
     * Because of this, anyone could skip the setter and write
     * an invalid value. Therefore, we must also enforce the
     * minimum in the getter.
     */
    private fun ensureMinimumCheckInterval(
        rawMillis: Long
    ): Long {
        return max(rawMillis, MIN_CHECK_INTERVAL_MILLIS)
    }
}