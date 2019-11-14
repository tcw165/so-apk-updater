package co.sodalabs.updaterengine

import android.provider.Settings

/**
 * Keys for the data storing in [android.provider.Settings].
 * Note: System might have string length (32 characters) or dictionary limitation.
 */
object SharedSettingsProps {

    const val DEVICE_ID = "device_id"
    const val DEVICE_PROVISIONED = Settings.Global.DEVICE_PROVISIONED

    const val USER_SETUP_COMPLETE = "user_setup_complete"

    const val ADMIN_PASSCODE = "admin_passcode"
    const val ADMIN_FORCEFULLY_LOGGABLE = "forcefully_loggable"

    @Deprecated("Use [SPARKPOINT_REST_API_BASE_URL] instead")
    const val SERVER_ENVIRONMENT = "server_environment"

    const val SPARKPOINT_REST_API_BASE_URL = "sparkpoint_rest_api_base_url"
}