package co.sodalabs.updaterengine.extension

import co.sodalabs.updaterengine.exception.DeviceNotSetupException
import timber.log.Timber
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object TimberExt {

    /**
     * Conditionally print warnings and errors.
     */
    fun warnOnKnownElseError(
        error: Throwable
    ) {
        if (error is DeviceNotSetupException ||
            error is SocketTimeoutException ||
            error is UnknownHostException) {
            Timber.w(error)
        } else {
            Timber.e(error)
        }
    }
}
