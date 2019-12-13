package co.sodalabs.updaterengine

object Intervals {

    const val SAMPLE_INTERVAL_SHORT = 500L
    const val SAMPLE_INTERVAL_NORMAL = 1000L
    const val SAMPLE_INTERVAL_LONG = 5000L

    const val RETRY_AFTER_1S = 1000L

    const val DELAY_ONE_SECOND = 1000L

    const val DEBOUNCE_VALUE_CHANGE = 250L
    const val DEBOUNCE_DATETIME_CHANGE = 5000L

    const val TIMEOUT_COMMON = 350L
    const val TIMEOUT_SERVICE_BINDING = 5000L
    const val TIMEOUT_DOWNLOAD_HR = 5L
    const val TIMEOUT_INSTALL_MIN = 3L
    const val TIMEOUT_UPLOAD_MIN = 10L

    const val AUTO_EXIT = 60 * 1000L

    const val RETRY_CHECK = 3000L
    const val RETRY_DOWNLOAD = 3000L
    const val RETRY_INSTALL = 3000L

    // The ADB commands run in a separate process and take a couple of milliseconds (~2-5) to
    // complete, this delay of 500ms ensures that we react after the ADB command is executed
    const val DELAY_ADB = 500L
}