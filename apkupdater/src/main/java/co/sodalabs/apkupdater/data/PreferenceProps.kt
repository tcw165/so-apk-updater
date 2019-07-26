package co.sodalabs.apkupdater.data

import androidx.annotation.Keep
import co.sodalabs.apkupdater.BuildConfig

@Keep
object PreferenceProps {

    private const val PREFIX = BuildConfig.APPLICATION_ID

    const val NETWORK_CONNECTION_TIMEOUT_SECONDS = "$PREFIX.network_connection_timeout"
    const val NETWORK_READ_TIMEOUT_SECONDS = "$PREFIX.network_read_timeout"
    const val NETWORK_WRITE_TIMEOUT_SECONDS = "$PREFIX.network_write_timeout"

    const val HEARTBEAT_INTERVAL_SECONDS = "$PREFIX.heartbeat_interval"
    const val UPDATE_CHECK_INTERVAL_SECONDS = "$PREFIX.update_check_interval"
}