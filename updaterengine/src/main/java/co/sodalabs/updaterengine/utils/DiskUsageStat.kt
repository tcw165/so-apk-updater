package co.sodalabs.updaterengine.utils

import kotlin.math.roundToLong

data class DiskUsageStat(
    val name: String,
    val total: String,
    val used: String,
    val free: String,
    val blockSize: String
) {

    val totalInBytes: Long
        get() = getStorageValueInBytes(total)

    val usedInBytes: Long
        get() = getStorageValueInBytes(used)

    val freeInBytes: Long
        get() = getStorageValueInBytes(free)

    /**
     * Converts storage string to numeric value in bytes
     *
     * @param sizeWithUnit The string value with unit (e.g. 1GB, 2MB, 3KB)
     */
    private fun getStorageValueInBytes(sizeWithUnit: String): Long {
        val unit = sizeWithUnit.last()
        val value = sizeWithUnit.take(sizeWithUnit.length - 1).toFloat().roundToLong()
        val multiplier = when (unit) {
            'G' -> 1073741824
            'M' -> 1048576
            'K' -> 1024
            else -> throw IllegalArgumentException("Unknown unit $unit")
        }
        return value * multiplier
    }

    companion object {
        fun fromString(s: String): DiskUsageStat? {
            val items = s.split(" ")
                .filter { it.isNotEmpty() }
                .takeLast(5)
            return if (items.size == 5) {
                DiskUsageStat(
                    name = items[0],
                    total = items[1],
                    used = items[2],
                    free = items[3],
                    blockSize = items[4]
                )
            } else {
                null
            }
        }
    }
}