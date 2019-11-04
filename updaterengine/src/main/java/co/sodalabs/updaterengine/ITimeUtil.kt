package co.sodalabs.updaterengine

interface ITimeUtil {
    fun nowEpoch(): Long
    fun nowEpochFormatted(): String
    fun toDuration(durationInMillis: Long): String
}