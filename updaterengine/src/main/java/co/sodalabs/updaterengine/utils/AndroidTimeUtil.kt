package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.ITimeUtil
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.format.FormatStyle
import javax.inject.Inject

class AndroidTimeUtil @Inject constructor() : ITimeUtil {

    private val formatter by lazy {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)
            .withZone(ZoneOffset.UTC)
    }

    override fun now(): Instant = Instant.now()

    override fun nowEpochMillis() = now().toEpochMilli()

    override fun nowEpochFormatted(): String = formatter.format(now())

    override fun systemZonedNow(): ZonedDateTime = now().atZone(ZoneId.systemDefault())

    override fun toDuration(durationInMillis: Long): String {
        val millis = durationInMillis % 1000
        val second = durationInMillis / 1000 % 60
        val minute = durationInMillis / (1000 * 60) % 60
        val hour = durationInMillis / (1000 * 60 * 60) % 24

        return String.format("%02d:%02d:%02d.%d", hour, minute, second, millis)
    }
}