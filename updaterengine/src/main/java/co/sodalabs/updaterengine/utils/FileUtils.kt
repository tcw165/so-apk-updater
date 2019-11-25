package co.sodalabs.updaterengine.utils

import android.content.Context
import android.text.format.Formatter
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val BIT_COUNT_IN_MB = 20

class FileUtils @Inject constructor(
    val context: Context
) {

    fun isExceedSize(file: File, maxSize: Long): Boolean {
        val fileSize = file.length()
        Timber.i("[FileUtils] Max Size: ${formattedFileSize(maxSize)} | File Size: ${formattedFileSize(fileSize)}")
        return fileSize > maxSize
    }

    fun isOlderThanDuration(createdOnInMillis: Long, daysInMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val createdOnPlusExpiryPeriod = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(createdOnInMillis),
            zoneId)
            .plusDays(TimeUnit.MILLISECONDS.toDays(daysInMillis))
        val currentTimeMillis = ZonedDateTime.now(zoneId)
        Timber.i("[FileUtils] Time Since Last Modification: ${createdOnPlusExpiryPeriod.toLocalDateTime()} | Current Time: ${currentTimeMillis.toLocalDateTime()}")
        return createdOnPlusExpiryPeriod.isBefore(currentTimeMillis)
    }

    fun sizeInMb(file: File): Int {
        val sizeInMb = file.length().shr(BIT_COUNT_IN_MB).toInt()
        Timber.i("[FileUtils] File Size: ${formattedFileSize(file.length())} Bytes | Size in MBs: $sizeInMb")
        return sizeInMb
    }

    private fun formattedFileSize(size: Long): String {
        return Formatter.formatShortFileSize(context, size)
    }
}
