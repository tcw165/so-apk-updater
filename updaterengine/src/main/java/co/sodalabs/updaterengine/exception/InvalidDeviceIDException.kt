package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class InvalidDeviceIDException(
    deviceID: String
) : RuntimeException("Invalid device ID '$deviceID'")