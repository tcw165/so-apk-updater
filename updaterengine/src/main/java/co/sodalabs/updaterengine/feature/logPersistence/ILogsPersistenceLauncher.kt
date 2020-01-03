package co.sodalabs.updaterengine.feature.logPersistence

import java.util.UUID

interface ILogsPersistenceLauncher {
    fun schedulePeriodicBackingUpLogToCloud()
    fun cancelPendingAndRunningBackingUp()

    fun backupLogToCloudNow(): UUID
}