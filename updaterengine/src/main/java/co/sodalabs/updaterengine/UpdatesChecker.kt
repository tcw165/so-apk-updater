package co.sodalabs.updaterengine

interface UpdatesChecker {
    fun checkNow(packageNames: List<String>)
    fun scheduleDelayedCheck(packageNames: List<String>, delayMillis: Long)
}