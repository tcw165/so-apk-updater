package co.sodalabs.updaterengine

import io.reactivex.Observable

interface AppUpdaterHeartBeater {

    /**
     * Send heart beat and return the HTTP response code.
     */
    fun sendHeartBeatNow()

    /**
     * Schedule a recurring heart beat and return a sequence of response code over time.
     */
    fun scheduleRecurringHeartBeat(intervalMs: Long, sendImmediately: Boolean)

    /**
     * Observe the heart beat response code over time.
     */
    fun observeRecurringHeartBeat(): Observable<Int>
}