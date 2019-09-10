package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.extension.isGreaterThan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionExtensionsTest {

    @Test
    fun `version set with same length`() {
        assertTrue("0.0.2".isGreaterThan("0.0.1", orEqualTo = true))
        assertTrue("1.0.3".isGreaterThan("0.99.99", orEqualTo = true))
        assertTrue("11.22.33".isGreaterThan("11.22.32", orEqualTo = true))
        assertTrue("12.34.56.78".isGreaterThan("12.34.56.78", orEqualTo = true))
    }

    @Test
    fun `version set with different lengths`() {
        assertTrue("1.2.4".isGreaterThan("1.2.3.99", orEqualTo = true)) // Consider 1.2.4 is 1.2.4.0
        assertTrue("1.2.33.4".isGreaterThan("1.2.6", orEqualTo = true))
    }

    @Test
    fun `version set with alphabets`() {
        assertTrue("0.0.2-beta".isGreaterThan("0.0.1-alpha", orEqualTo = true))
        assertFalse("0.0.1-alpha".isGreaterThan("0.0.2-beta", orEqualTo = true))
    }

    @Test
    fun `version set with typos`() {
        assertTrue("0-noob-typo.0.2".isGreaterThan("0.0.1-beta", orEqualTo = true))
        assertTrue("0 .   0.2".isGreaterThan("0.0.2", orEqualTo = true))
        assertTrue("1.2.-3-3*.4".isGreaterThan("1.2.6**", orEqualTo = true))
    }

    @Test
    fun `version greater-than only`() {
        assertTrue("0.1.0.0".isGreaterThan("0.0.2.0", orEqualTo = false))
        assertTrue("0.0.2.1".isGreaterThan("0.0.2.0", orEqualTo = false))
        assertFalse("0.0.2.0".isGreaterThan("0.0.2.0", orEqualTo = false))
        assertFalse("0.0.2.1".isGreaterThan("0.0.2.1", orEqualTo = false))
    }
}