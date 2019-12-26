package co.sodalabs.updaterengine

import androidx.annotation.Keep

@Keep
object PreferenceProps {

    private const val PREFIX = "co.sodalabs"

    const val NETWORK_CONNECTION_TIMEOUT_SECONDS = "$PREFIX.network_connection_timeout"
    const val NETWORK_READ_TIMEOUT_SECONDS = "$PREFIX.network_read_timeout"
    const val NETWORK_WRITE_TIMEOUT_SECONDS = "$PREFIX.network_write_timeout"

    const val LAST_KNOWN_FIRMWARE_VERSION = "$PREFIX.last_known_firmware_version"

    // API ////////////////////////////////////////////////////////////////////

    const val API_BASE_URL = "$PREFIX.api_base_url"
    const val API_UPDATE_CHANNEL = "$PREFIX.update_channel"

    // Mocking Info ///////////////////////////////////////////////////////////

    const val MOCK_DEVICE_ID = "$PREFIX.mock_device_id"
    const val MOCK_FIRMWARE_VERSION = "$PREFIX.mock_firmware_version"
    const val MOCK_USER_SETUP_INCOMPLETE = "$PREFIX.mock_user_setup_incomplete"
    const val MOCK_SPARKPOINT_VERSION = "$PREFIX.mock_sparkpoint_version"

    // Heartbeat //////////////////////////////////////////////////////////////

    const val HEARTBEAT_INTERVAL_SECONDS = "$PREFIX.heartbeat_interval"
    const val HEARTBEAT_VERBAL_RESULT = "$PREFIX.heartbeat_verbal_result"

    // Updater ////////////////////////////////////////////////////////////////

    const val CHECK_INTERVAL_SECONDS = "$PREFIX.check_interval"

    const val DOWNLOAD_USE_CACHE = "$PREFIX.download_use_cache"
    const val DOWNLOAD_CACHE_MAX_SIZE_MB = "$PREFIX.download_cache_max_size"

    const val INSTALL_HOUR_BEGIN = "$PREFIX.install_hour_begin"
    const val INSTALL_HOUR_END = "$PREFIX.install_hour_end"
    const val INSTALL_SILENTLY = "$PREFIX.install_silently"
    const val INSTALL_ALLOW_DOWNGRADE = "$PREFIX.install_allow_downgrade"

    const val SESSION_ID = "$PREFIX.session_id"

    // Log Persistence ////////////////////////////////////////////////////////

    const val LOG_FILE_CREATED_TIMESTAMP = "$PREFIX.log_file_created_on"
}