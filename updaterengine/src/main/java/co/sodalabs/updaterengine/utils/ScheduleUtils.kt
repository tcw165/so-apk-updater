package co.sodalabs.updaterengine.utils

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import timber.log.Timber

const val DELAY_ONE_SECOND = 1000L

object ScheduleUtils {

    @Suppress("ReplaceRangeStartEndInclusiveWithFirstLast", "ReplaceJavaStaticMethodWithKotlinAnalog", "ConvertTwoComparisonsToRangeCheck")
    fun findNextInstallTimeMillis(
        installWindow: IntRange
    ): Long {
        val currentDateTime = Instant.now()
            .atZone(ZoneId.systemDefault())
        val hour = currentDateTime.hour
        // Convert the window to Calendar today.
        val startHour = installWindow.start
        val endHour = installWindow.endInclusive

        val isWithinInterval = when {
            startHour == endHour -> {
                // Interval is a full day, it's within of course.
                true
            }
            startHour < endHour -> {
                // Interval is within a day.
                hour >= startHour && hour < endHour
            }
            else -> {
                // Interval spans over a day.
                hour >= startHour || hour < endHour
            }
        }

        return if (isWithinInterval) {
            val triggerAtMillis = currentDateTime.toInstant().toEpochMilli() + DELAY_ONE_SECOND
            Timber.v("[Updater] It's $currentDateTime and is currently within the install window, [$startHour..$endHour], will install the updates after $DELAY_ONE_SECOND milliseconds")
            triggerAtMillis
        } else {
            // If the current time has past the window today, schedule for tomorrow
            val plusDays = if (hour < startHour) 0L else 1L
            val triggerDateTime = currentDateTime
                .withHour(startHour)
                .withMinute(0)
                .withSecond(0)
                .plusDays(plusDays)
            val triggerAtMillis = triggerDateTime.toInstant().toEpochMilli()

            Timber.v("[Updater] It's $currentDateTime and is outside the install window, [$startHour..$endHour], will install the updates at $triggerDateTime, which is $triggerAtMillis milliseconds after")
            triggerAtMillis
        }
    }
}