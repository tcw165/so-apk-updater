package co.sodalabs.updaterengine

interface UpdatesChecker {
    fun checkNow()
    @Deprecated("The delayed check is now managed by the UpdaterService. Don't use it since it's soon removed")
    fun scheduleCheck(triggerAtMillis: Long)
}