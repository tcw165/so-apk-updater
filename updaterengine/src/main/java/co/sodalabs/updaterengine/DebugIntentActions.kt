package co.sodalabs.updaterengine

object DebugIntentActions {
    // For internal use.
    const val ACTION_INSTALL_FIRMWARE_UPDATE = "$ACTION_PREFIX.debug_install_firmware_update"

    // For external use.
    const val ACTION_INSTALL_FULL_FIRMWARE_UPDATE = "install_full_update"
    const val ACTION_INSTALL_INCREMENTAL_FIRMWARE_UPDATE = "install_incremental_update"

    const val PROP_FILE = "file"
}