package co.sodalabs.updaterengine.extension

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId

fun getPrettyDateNow(): String {
    val currentDateTime = Instant.now()
        .atZone(ZoneId.systemDefault())
    return currentDateTime.toString()
}