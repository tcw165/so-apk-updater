package co.sodalabs.apkupdater.feature.watchdog

interface IForegroundAppWatchdogLauncher {
    fun correctNowThenCheckPeriodically()
    fun schedulePeriodicallyCorrection()
    fun cancelPendingAndOnGoingValidation()
}