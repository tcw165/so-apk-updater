package co.sodalabs.updaterengine

object IntentActions {

    // Check/download/install requests
    const val ACTION_ENGINE_START = "$ACTION_PREFIX.engine_start"
    const val ACTION_CHECK_UPDATES = "$ACTION_PREFIX.check_updates"
    const val ACTION_CHECK_UPDATES_COMPLETE = "$ACTION_PREFIX.check_updates_complete"
    const val ACTION_DOWNLOAD_UPDATES = "$ACTION_PREFIX.download_updates"
    const val ACTION_DOWNLOAD_UPDATES_COMPLETE = "$ACTION_PREFIX.download_updates_complete"
    const val ACTION_INSTALL_UPDATES = "$ACTION_PREFIX.install_updates"
    const val ACTION_INSTALL_UPDATES_COMPLETE = "$ACTION_PREFIX.install_updates_complete"
    const val ACTION_UNINSTALL_PACKAGES = "$ACTION_PREFIX.uninstall_packages"
    const val ACTION_UNINSTALL_PACKAGES_COMPLETE = "$ACTION_PREFIX.uninstall_packages_complete"
    // Heart-beat
    const val ACTION_SEND_HEART_BEAT_NOW = "$ACTION_PREFIX.send_heart_beat_now"

    const val PROP_RESET_UPDATER_SESSION = "$EXTRA_PREFIX.reset_updater_session"

    const val PROP_FOUND_UPDATES = "$ACTION_PREFIX.found_updates"
    const val PROP_DOWNLOADED_UPDATES = "$EXTRA_PREFIX.downloaded_updates"
    const val PROP_APPLIED_UPDATES = "$EXTRA_PREFIX.applied_updates"
    const val PROP_APP_PACKAGE_NAMES = "$EXTRA_PREFIX.app_package_names"

    const val PROP_HTTP_RESPONSE_CODE = "$EXTRA_PREFIX.http_response_code"
    const val PROP_ERROR = "$EXTRA_PREFIX.error"
}