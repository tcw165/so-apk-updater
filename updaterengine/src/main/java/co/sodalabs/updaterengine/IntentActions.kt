package co.sodalabs.updaterengine

object IntentActions {

    // Check/install requests
    const val ACTION_CHECK_UPDATES = "$ACTION_PREFIX.check_updates"
    const val ACTION_INSTALL_APP = "$ACTION_PREFIX.install_app"
    const val ACTION_UNINSTALL_APP = "$ACTION_PREFIX.uninstall_app"
    // Check response broadcast
    const val ACTION_UPDATES_FOUND = "$ACTION_PREFIX.updates_found"
    const val ACTION_UPDATES_NOT_FOUND = "$ACTION_PREFIX.updates_not_found"

    // Install response broadcast
    const val ACTION_INSTALL_SUCCESSFULLY = "$ACTION_PREFIX.install_successfully"
    const val ACTION_INSTALL_CANCELLED = "$ACTION_PREFIX.install_cancelled"
    const val ACTION_INSTALL_FAILED = "$ACTION_PREFIX.install_failed"
    const val ACTION_UNINSTALL_SUCCESSFULLY = "$ACTION_PREFIX.uninstall_successfully"
    const val ACTION_UNINSTALL_CANCELLED = "$ACTION_PREFIX.uninstall_cancelled"
    const val ACTION_UNINSTALL_FAILED = "$ACTION_PREFIX.uninstall_failed"

    // Install response broadcast
    const val ACTION_SCHEDULE_HEART_BEAT = "$ACTION_PREFIX.schedule_heart_beat"
    // Check response broadcast
    const val ACTION_SEND_HEART_BEAT_NOW = "$ACTION_PREFIX.send_heart_beat_now"

    const val PROP_APP_FILE_URI = "$EXTRA_PREFIX.app_file_uri"
    const val PROP_APP_PACKAGE_NAME = "$EXTRA_PREFIX.app_package_name"
    const val PROP_APP_PACKAGE_NAMES = "$EXTRA_PREFIX.app_package_names"
    const val PROP_APP_UPDATES = "$EXTRA_PREFIX.app_updates"
}