package co.sodalabs.apkupdater.feature.remoteConfig

import timber.log.Timber
import java.util.TimeZone
import javax.inject.Inject

private const val SPACE = ' '
private val VALIDATOR_REGEX = Regex("^.+\\sGMT[+-][0-9]{1,2}:[0-9]{1,2}\$")

/**
 * This static util for mapping the timezone string defined within Sparkpoint.
 */
class SparkpointTimezoneMapper @Inject constructor(
    // No argument
) : ITimezoneMapper {

    /**
     * A cheap cache for mapping the city given by server to the local city.
     * The map cuts a lot of redundant computation cause this method is called
     * quite frequently.
     */
    private val remoteCityToLocalCityMap = mutableMapOf<String, String>()

    override fun extractTimezoneCity(
        timezoneString: String
    ): String? {
        if (!timezoneString.matches(VALIDATOR_REGEX)) {
            Timber.e("'$timezoneString' is invalid")
            return null
        }

        val (city, offset) = timezoneString.split(SPACE)
        if (!remoteCityToLocalCityMap.containsKey(city)) {
            val availableIDs = TimeZone.getAvailableIDs()
            availableIDs
                .firstOrNull { id -> id.contains(city, ignoreCase = true) }
                ?.let { mappedCity ->
                    remoteCityToLocalCityMap[city] = mappedCity
                }
        }

        return remoteCityToLocalCityMap[city]
    }
}