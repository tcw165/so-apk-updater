package co.sodalabs.updaterengine.exception

data class DeviceNotSetupException(
    val deviceID: String
) : RuntimeException("Device (ID: $deviceID) isn't yet fully setup yet")