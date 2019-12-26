package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class InstallFirmwareException(msg: String) : RuntimeException(msg)