package co.sodalabs.apkupdater

import androidx.annotation.Keep

@Keep
object PreferenceProps {

    private const val PREFIX = BuildConfig.APPLICATION_ID

    const val NETWORK_CONNECTION_TIMEOUT_SECONDS = "$PREFIX.network_connection_timeout"
    const val NETWORK_READ_TIMEOUT_SECONDS = "$PREFIX.network_read_timeout"
    const val NETWORK_WRITE_TIMEOUT_SECONDS = "$PREFIX.network_write_timeout"

    const val HEARTBEAT_INTERVAL_SECONDS = "$PREFIX.heartbeat_interval"

    const val CHECK_INTERVAL_SECONDS = "$PREFIX.check_interval"

    const val DOWNLOAD_USE_CACHE = "$PREFIX.download_use_cache"
    const val DOWNLOAD_CACHE_MAX_SIZE_MB = "$PREFIX.download_cache_max_size"

    const val INSTALL_HOUR_BEGIN = "$PREFIX.install_hour_begin"
    const val INSTALL_HOUR_END = "$PREFIX.install_hour_end"
    const val INSTALL_ALLOW_DOWNGRADE = "$PREFIX.install_allow_downgrade"
}