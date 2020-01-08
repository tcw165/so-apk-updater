package co.sodalabs.apkupdater.feature.watchdog

interface IForegroundAppWatchdogLauncher {
    /**
     * Correcting foreground Activity include two things:
     * 1. Set the right default launcher.
     * 2. Start that default launcher.
     */
    fun correctNowThenCheckPeriodically()

    fun schedulePeriodicallyCorrection()
    fun cancelPendingAndOnGoingValidation()
}