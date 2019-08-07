package co.sodalabs.updaterengine

interface AppUpdatesChecker {
    fun checkNow(packageNames: List<String>)
    fun scheduleCheckAfter(packageNames: List<String>, afterMs: Long)
    fun scheduleRecurringCheck(packageNames: List<String>, intervalMs: Long)
}