package co.sodalabs.updaterengine.extension

fun String.isGreaterThan(
    other: String,
    orEqualTo: Boolean
): Boolean {

    // For example:
    // a: 12.23.34.1 vs
    // b: 12.23.35
    // b is greater.

    var i = 0
    var j = 0
    var numI = 0
    var numJ = 0

    // Time complexity: O(n)
    while (i < this.length || j < other.length) {
        val charI = if (i < this.length) {
            this[i]
        } else {
            // The algorithm use delimiter as notation to stop one cursor and
            // continue collecting number for the other cursor.
            '.'
        }
        val charJ = if (j < other.length) other[j] else '.'

        // Accumulate the segment number
        if (charI.isNumeric()) {
            numI += 10 * numI + charI.toNumeric()
        }
        if (charJ.isNumeric()) {
            numJ += 10 * numJ + charJ.toNumeric()
        }

        if (charI.isDelimiter() && charJ.isDelimiter()) {
            // Compare two numbers
            if (numI > numJ) {
                return true
            } else if (numI < numJ) {
                return false
            }

            // The number is equal, we gotta continue

            // Reset the cache
            numI = 0
            numJ = 0
            // Then move on
            ++i
            ++j

            // Also, if one version string is already exhausted, exit the loop.
            if (numI != 0 && numJ != 0 &&
                (i >= this.length || j >= other.length)) {
                break
            }
        } else {
            if (!charI.isDelimiter()) {
                ++i
            }
            if (!charJ.isDelimiter()) {
                ++j
            }
        }
    }

    // The last segment
    return if (orEqualTo) {
        numI >= numJ
    } else {
        numI > numJ
    }
}

fun Char.isDelimiter(): Boolean {
    return this == '.'
}

fun Char.isNumeric(): Boolean {
    return this in '0'..'9'
}

fun Char.toNumeric(): Int {
    return this - '0'
}