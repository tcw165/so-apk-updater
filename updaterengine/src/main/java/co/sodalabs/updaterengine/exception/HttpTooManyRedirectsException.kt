package co.sodalabs.updaterengine.exception

data class HttpTooManyRedirectsException(
    val url: String
) : RuntimeException("Too many redirects for \"$url\"")