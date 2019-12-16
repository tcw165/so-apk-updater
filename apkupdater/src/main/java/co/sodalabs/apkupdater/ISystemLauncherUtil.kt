package co.sodalabs.apkupdater

interface ISystemLauncherUtil {
    fun startSystemLauncherWithSelector()
    fun startSodaLabsLauncherIfInstalled()
    fun setSodaLabsLauncherAsDefaultIfInstalled()
}