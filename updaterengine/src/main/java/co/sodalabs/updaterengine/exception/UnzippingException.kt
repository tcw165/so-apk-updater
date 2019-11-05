package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class UnzippingException(
    msg: String
) : RuntimeException(msg)