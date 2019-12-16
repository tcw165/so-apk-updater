package co.sodalabs.apkupdater.feature.homeCorrector

interface IHomeCorrectorLauncher {
    fun scheduleStartingSodaLabsLauncher(delayMillis: Long)
    fun correctDefaultHomeNow()
}