package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.extension.isGreaterThanOrEqualTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionExtensionsTest {

    @Test
    fun `version set with same length`() {
        assertTrue("0.0.2".isGreaterThanOrEqualTo("0.0.1"))
        assertTrue("1.0.3".isGreaterThanOrEqualTo("0.99.99"))
        assertTrue("11.22.33".isGreaterThanOrEqualTo("11.22.32"))
        assertTrue("12.34.56.78".isGreaterThanOrEqualTo("12.34.56.78"))
    }

    @Test
    fun `version set with different lengths`() {
        assertTrue("1.2.4".isGreaterThanOrEqualTo("1.2.3.99")) // Consider 1.2.4 is 1.2.4.0
        assertTrue("1.2.33.4".isGreaterThanOrEqualTo("1.2.6"))
    }

    @Test
    fun `version set with alphabets`() {
        assertTrue("0.0.2-beta".isGreaterThanOrEqualTo("0.0.1-alpha"))
        assertFalse("0.0.1-alpha".isGreaterThanOrEqualTo("0.0.2-beta"))
    }

    @Test
    fun `version set with typos`() {
        assertTrue("0-noob-typo.0.2".isGreaterThanOrEqualTo("0.0.1-beta"))
        assertTrue("0 .   0.2".isGreaterThanOrEqualTo("0.0.2"))
        assertTrue("1.2.-3-3*.4".isGreaterThanOrEqualTo("1.2.6**"))
    }
}