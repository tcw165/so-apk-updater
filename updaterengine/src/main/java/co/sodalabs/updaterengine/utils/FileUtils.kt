package co.sodalabs.updaterengine.utils

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val BIT_COUNT_IN_MB = 20

class FileUtils @Inject constructor() {

    fun isExceedSize(file: File, maxSizeInMb: Int): Boolean {
        val fileSizeInMb = sizeInMb(file)
        Timber.i("Max Size: $maxSizeInMb | File Size: $fileSizeInMb")
        return fileSizeInMb > maxSizeInMb
    }

    fun isOlderThanDuration(createdOn: Long, days: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val createdOnPlusExpiryPeriod = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(createdOn),
            zoneId)
            .plusDays(days)
        val currentTimeMillis = ZonedDateTime.now(zoneId)
        Timber.i("Time Since Last Modification: ${createdOnPlusExpiryPeriod.toLocalDateTime()} | Current Time: ${currentTimeMillis.toLocalDateTime()}")
        return createdOnPlusExpiryPeriod.isBefore(currentTimeMillis)
    }

    fun sizeInMb(file: File): Int {
        val sizeInMb = file.length().shr(BIT_COUNT_IN_MB).toInt()
        Timber.i("File Size: ${file.length()} Bytes | Size in MBs: $sizeInMb")
        return sizeInMb
    }
}
