package co.sodalabs.apkupdater.feature.watchdog

interface IForegroundAppWatchdogLauncher {
    fun scheduleForegroundProcessValidation()
    fun cancelPendingAndOnGoingValidation()
}