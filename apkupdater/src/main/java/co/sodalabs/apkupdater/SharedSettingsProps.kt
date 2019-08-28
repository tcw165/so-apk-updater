package co.sodalabs.apkupdater

import android.provider.Settings

/**
 * Keys for the data storing in [android.provider.Settings].
 * Note: System might have string length (32 characters) or dictionary limitation.
 */
object SharedSettingsProps {

    const val DEVICE_ID = "device_id"
    const val DEVICE_PROVISIONED = Settings.Global.DEVICE_PROVISIONED

    const val USER_SETUP_COMPLETE = "user_setup_complete"
}