package co.sodalabs.updaterengine

object Intervals {

    const val SAMPLE_INTERVAL_SHORT = 500L
    const val SAMPLE_INTERVAL_NORMAL = 1000L
    const val SAMPLE_INTERVAL_LONG = 5000L

    const val RETRY_AFTER_1S = 1000L

    const val DEBOUNCE_VALUE_CHANGE = 250L
    const val DEBOUNCE_DATETIME_CHANGE = 5000L

    const val TIMEOUT_COMMON = 350L
    const val TIMEOUT_SERVICE_BINDING = 5000L
    const val TIMEOUT_DOWNLOAD_HR = 5L
    const val TIMEOUT_INSTALL_MIN = 3L

    const val RETRY_CHECK = 3000L
    const val RETRY_DOWNLOAD = 3000L
    const val RETRY_INSTALL = 3000L
}