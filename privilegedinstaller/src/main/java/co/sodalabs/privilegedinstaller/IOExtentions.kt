package co.sodalabs.privilegedinstaller

import android.annotation.TargetApi

/**
 * Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null.
 */
@TargetApi(24)
fun AutoCloseable?.closeQuietly() {
    if (this != null) {
        try {
            this.close()
        } catch (rethrown: RuntimeException) {
            throw rethrown
        } catch (ignored: Exception) {
        }
    }
}