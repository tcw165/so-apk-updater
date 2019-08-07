package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class HttpTooManyRedirectsException(
    val url: String
) : RuntimeException("Too many redirects for \"$url\"")