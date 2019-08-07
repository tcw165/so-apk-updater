package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class RxServiceConnectionError(
    val componentName: String
) : RuntimeException("Unable to bind $componentName")