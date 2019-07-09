package co.sodalabs.updaterengine.exception

data class HttpMalformedURIException(
    val url: String
) : RuntimeException("Malformed URI: \"$url\"")