package co.sodalabs.updaterengine

interface UpdatesChecker {
    fun checkNow()
    fun scheduleCheck(triggerAtMillis: Long)
}