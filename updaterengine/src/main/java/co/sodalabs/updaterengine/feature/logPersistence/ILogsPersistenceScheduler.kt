package co.sodalabs.updaterengine.feature.logPersistence

import io.reactivex.Observable

interface ILogsPersistenceScheduler {
    fun start()
    fun stop()
    fun triggerImmediate(filePath: String): Observable<Boolean>
}