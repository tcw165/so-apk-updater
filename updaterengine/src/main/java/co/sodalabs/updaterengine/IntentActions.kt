package co.sodalabs.updaterengine

object IntentActions {

    const val ACTION_ENGINE_START = "$ACTION_PREFIX.engine_start"
    // General update check
    const val ACTION_CHECK_UPDATE = "$ACTION_PREFIX.check_update"
    // App update download & install
    const val ACTION_CHECK_APP_UPDATE_COMPLETE = "$ACTION_PREFIX.check_app_update_complete"
    const val ACTION_DOWNLOAD_APP_UPDATE = "$ACTION_PREFIX.download_app_update"
    const val ACTION_DOWNLOAD_APP_UPDATE_PROGRESS = "$ACTION_PREFIX.download_app_update_progress"
    const val ACTION_DOWNLOAD_APP_UPDATE_COMPLETE = "$ACTION_PREFIX.download_app_update_complete"
    const val ACTION_INSTALL_APP_UPDATE = "$ACTION_PREFIX.install_app_update"
    const val ACTION_INSTALL_APP_UPDATE_COMPLETE = "$ACTION_PREFIX.install_app_update_complete"
    const val ACTION_UNINSTALL_PACKAGES = "$ACTION_PREFIX.uninstall_packages"
    const val ACTION_UNINSTALL_PACKAGES_COMPLETE = "$ACTION_PREFIX.uninstall_packages_complete"
    // Firmware update download & install
    const val ACTION_CHECK_FIRMWARE_UPDATE_COMPLETE = "$ACTION_PREFIX.check_firmware_update_complete"
    const val ACTION_CHECK_FIRMWARE_UPDATE_ERROR = "$ACTION_PREFIX.check_firmware_update_error"
    const val ACTION_DOWNLOAD_FIRMWARE_UPDATE = "$ACTION_PREFIX.download_firmware_update"
    const val ACTION_DOWNLOAD_FIRMWARE_UPDATE_PROGRESS = "$ACTION_PREFIX.download_firmware_update_progress"
    const val ACTION_DOWNLOAD_FIRMWARE_UPDATE_COMPLETE = "$ACTION_PREFIX.download_firmware_update_complete"
    const val ACTION_DOWNLOAD_FIRMWARE_UPDATE_ERROR = "$ACTION_PREFIX.download_firmware_update_error"
    const val ACTION_INSTALL_FIRMWARE_UPDATE = "$ACTION_PREFIX.install_firmware_update"
    const val ACTION_INSTALL_FIRMWARE_UPDATE_COMPLETE = "$ACTION_PREFIX.install_firmware_update_complete"
    const val ACTION_INSTALL_FIRMWARE_UPDATE_ERROR = "$ACTION_PREFIX.install_firmware_update_error"
    // Heart-beat
    const val ACTION_SEND_HEART_BEAT_NOW = "$ACTION_PREFIX.send_heart_beat_now"

    const val PROP_RESET_UPDATER_SESSION = "$EXTRA_PREFIX.reset_updater_session"

    // Used for firmware, since we only ever have one at a time
    const val PROP_FOUND_UPDATE = "$ACTION_PREFIX.found_update"
    // Used for apks, since we have batch installs
    const val PROP_FOUND_UPDATES = "$ACTION_PREFIX.found_updates"
    const val PROP_DOWNLOADED_UPDATE = "$EXTRA_PREFIX.downloaded_update"
    const val PROP_DOWNLOADED_UPDATES = "$EXTRA_PREFIX.downloaded_updates"
    const val PROP_APPLIED_UPDATE = "$EXTRA_PREFIX.applied_update"
    const val PROP_APPLIED_UPDATES = "$EXTRA_PREFIX.applied_updates"
    const val PROP_APP_PACKAGE_NAMES = "$EXTRA_PREFIX.app_package_names"

    const val PROP_HTTP_RESPONSE_CODE = "$EXTRA_PREFIX.http_response_code"
    const val PROP_ERROR = "$EXTRA_PREFIX.error"
    const val PROP_PROGRESS_PERCENTAGE = "$ACTION_PREFIX.progress_percentage"
    const val PROP_DOWNLOAD_CURRENT_BYTES = "$ACTION_PREFIX.download_current_bytes"
    const val PROP_DOWNLOAD_TOTAL_BYTES = "$ACTION_PREFIX.download_total_bytes"

    // TODO: Determine the following properties and pass them around (as immutable) in the session.
    const val PROP_INSTALL_SILENTLY = "$ACTION_PREFIX.install_silently"
    const val PROP_INSTALL_ALLOW_DOWNGRADE = "$ACTION_PREFIX.install_silently"
}