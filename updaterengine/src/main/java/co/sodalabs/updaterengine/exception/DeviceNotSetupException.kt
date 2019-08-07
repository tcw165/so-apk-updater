package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class DeviceNotSetupException(
    val deviceID: String
) : RuntimeException("Device (ID: $deviceID) isn't yet fully setup yet")