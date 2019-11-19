package co.sodalabs.updaterengine.feature.logPersistence

object LogsPersistenceConstants {
    const val WORK_NAME = "persist_log_request"
    const val WORK_TAG = "log_persistence"
    const val PARAM_TRIGGERED_BY_USER = "log_force_send"
    const val INVALID_CREATION_DATE = -1L

    const val LOG_DIR = "logs"
    const val LOG_FILE = "log.txt"

    // We use this temporary file to copy the LogCat contents to it and then
    // delete it after appending its contents to the actual log persistence fle
    const val TEMP_LOG_BUFFER_FILE = "temp-log-buffer.txt"
}