package co.sodalabs.apkupdater.net

import okhttp3.Interceptor
import okhttp3.Response

class HostResolutionInterceptor : Interceptor {

    override fun intercept(
        chain: Interceptor.Chain
    ): Response {
        val request = chain.request()
        val requestURL = request.url()
        val requestHost = requestURL.host()
        val newRequest = request.newBuilder()
            .url(requestURL)
            // Adding "Host" to the header could speed up the domain resolution.
            // Reference: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Host
            .addHeader("Host", requestHost)
            .build()
        return chain.proceed(newRequest)
    }
}