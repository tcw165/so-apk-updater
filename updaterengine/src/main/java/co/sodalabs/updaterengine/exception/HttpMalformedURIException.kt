package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class HttpMalformedURIException(
    val url: String
) : RuntimeException("Malformed URI: \"$url\"")