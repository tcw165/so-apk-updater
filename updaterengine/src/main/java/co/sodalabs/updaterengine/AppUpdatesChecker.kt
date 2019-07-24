package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate
import io.reactivex.Observable
import io.reactivex.Single

interface AppUpdatesChecker {

    fun checkNow(
        packageNames: List<String>
    ): Single<List<AppUpdate>>

    fun scheduleCheck(
        interval: Long
    ): Observable<List<AppUpdate>>
}