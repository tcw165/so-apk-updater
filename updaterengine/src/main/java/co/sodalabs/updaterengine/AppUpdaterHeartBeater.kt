package co.sodalabs.updaterengine

import io.reactivex.Observable
import io.reactivex.Single

interface AppUpdaterHeartBeater {

    /**
     * Send heart beat and return the HTTP response code.
     */
    fun sendHeartBeatNow(): Single<Int>

    /**
     * Schedule a recurring heart beat and return a sequence of response code over time.
     */
    fun scheduleRecurringHeartBeat(intervalMs: Long, sendImmediately: Boolean)

    /**
     * Observe the heart beat response code over time.
     */
    fun observeRecurringHeartBeat(): Observable<Int>
}