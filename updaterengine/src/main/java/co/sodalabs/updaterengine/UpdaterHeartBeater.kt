package co.sodalabs.updaterengine

import io.reactivex.Observable

interface UpdaterHeartBeater {

    /**
     * Send heart beat and return the HTTP response code.
     */
    fun sendHeartBeatNow()

    /**
     * Schedule a recurring heart beat and return a sequence of response code over time.
     */
    fun scheduleRecurringHeartBeat(intervalMs: Long)

    /**
     * Cancel recurring heart beat scheduling.
     */
    fun cancelRecurringHeartBeat()

    /**
     * Observe the heart beat response code over time.
     */
    fun observeRecurringHeartBeat(): Observable<Int>
}