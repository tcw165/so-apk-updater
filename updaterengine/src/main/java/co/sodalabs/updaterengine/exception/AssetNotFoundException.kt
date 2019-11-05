package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class AssetNotFoundException(
    msg: String
) : RuntimeException(msg)