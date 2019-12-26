package co.sodalabs.apkupdater.feature.remoteConfig

/**
 * This static util for mapping the timezone string defined within Sparkpoint.
 */
interface ITimezoneMapper {
    fun extractTimezoneCity(timezoneString: String): String?
}