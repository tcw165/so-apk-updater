package co.sodalabs.updaterengine.utils

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import timber.log.Timber

const val DELAY_ONE_SECOND = 1000L

const val BACKOFF_SECONDS = 5L
// BACKOFF_SECONDS ^ BACKOFF_RESET_THRESHOLD (given 5^3) is 125 seconds (~2 minutes)
const val BACKOFF_RESET_THRESHOLD = 3L

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

    fun findNextDownloadTimeMillis(
        attempts: Int
    ): Long {
        require(attempts >= 1) { "Invalid attempts $attempts" }

        // We want the power factor as a sequence [1,2,3, 1,2,3, 1,2,3, ...]
        // e.g.
        // Input:  1,2,3, 4,5,6, 7,8,9
        // Output: 1,2,3, 1,2,3, 1,2,3
        // If the base is 5 seconds, you'll see the backoff as
        // [5, 25, 125, 5, 25, 125, ...] seconds
        val throttledAttempts = (attempts - 1) % BACKOFF_RESET_THRESHOLD
        // The Math.pow requires casting to Double and then cast back to Long.
        // That's overkilled since we could just do it with a loop.
        // Time: O(n)
        var delay = BACKOFF_SECONDS
        for (i in 0 until throttledAttempts) {
            delay *= BACKOFF_SECONDS
        }
        val currentDateTime = Instant.now()
            .atZone(ZoneId.systemDefault())
        Timber.v("[Updater] Given $attempts attempts, retry after $delay seconds")
        val retryDateTime = currentDateTime.plusSeconds(delay)
        Timber.v("[Updater] It's $currentDateTime and will retry downloading at $retryDateTime")
        return retryDateTime.toInstant().toEpochMilli()
    }
}
