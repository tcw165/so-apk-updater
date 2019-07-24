package co.sodalabs.updaterengine

import io.reactivex.Observable

interface AppUpdaterHeartBeater {

    fun scheduleEvery(interval: Long, sendImmediately: Boolean): Observable<Unit>
}