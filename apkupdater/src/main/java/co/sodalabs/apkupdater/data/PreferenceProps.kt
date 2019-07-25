package co.sodalabs.apkupdater.data

import androidx.annotation.Keep
import co.sodalabs.apkupdater.BuildConfig

@Keep
object PreferenceProps {

    private const val PREFIX = BuildConfig.APPLICATION_ID

    const val NETWORK_CONNECTION_TIMEOUT = "$PREFIX.network_connection_timeout"
    const val NETWORK_READ_TIMEOUT = "$PREFIX.network_read_timeout"
    const val NETWORK_WRITE_TIMEOUT = "$PREFIX.network_write_timeout"
}