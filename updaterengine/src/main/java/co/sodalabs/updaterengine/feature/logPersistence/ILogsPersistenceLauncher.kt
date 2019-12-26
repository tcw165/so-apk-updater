package co.sodalabs.updaterengine.feature.logPersistence

import io.reactivex.Observable

interface ILogsPersistenceLauncher {
    fun scheduleBackingUpLogToCloud()
    fun cancelPendingAndRunningBackingUp()

    fun backupLogToCloudNow(): Observable<Boolean>
}