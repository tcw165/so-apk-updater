package co.sodalabs.apkupdater.utils

import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

/**
 * Bridge the HTTP interceptor to Timber logger.
 */
class HttpTimberLogger : HttpLoggingInterceptor.Logger {
    override fun log(message: String) {
        Timber.d(message)
    }
}