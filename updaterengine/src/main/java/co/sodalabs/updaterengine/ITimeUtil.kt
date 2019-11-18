package co.sodalabs.updaterengine

import org.threeten.bp.Instant
import org.threeten.bp.ZonedDateTime

/**
 * An abstraction around [Instant] for simplified unit testing.
 */
interface ITimeUtil {
    fun now(): Instant
    fun nowEpochMillis(): Long
    fun nowEpochFormatted(): String
    fun systemZonedNow(): ZonedDateTime

    fun toDuration(durationInMillis: Long): String
}