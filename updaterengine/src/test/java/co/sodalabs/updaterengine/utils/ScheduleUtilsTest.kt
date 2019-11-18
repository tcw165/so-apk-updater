package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.ITimeUtil
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val ONE_AM = 1
private const val TEN_AM = 10
private const val ONE_PM = 13
private const val TWO_PM = 14

@Suppress("PrivatePropertyName")
class ScheduleUtilsTest {

    @Mock
    lateinit var mockTimeUtil: ITimeUtil

    lateinit var scheduleUtils: ScheduleUtils

    private val MIDNIGHT_UTC = ZonedDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    private val NOON_UTC = ZonedDateTime.of(2000, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        scheduleUtils = ScheduleUtils(mockTimeUtil)
        Mockito.`when`(mockTimeUtil.systemZonedNow()).thenReturn(MIDNIGHT_UTC)
    }

    @Test
    fun `test isWithinWindow in and out of range`() {
        val fullDayRange = IntRange(ONE_AM, ONE_AM)
        Assert.assertTrue(scheduleUtils.isWithinWindow(fullDayRange))

        val inWindowOvernightRange = IntRange(TEN_AM, ONE_AM)
        Assert.assertTrue(scheduleUtils.isWithinWindow(inWindowOvernightRange))

        val outOfWindowOvernightRange = IntRange(ONE_AM, TEN_AM)
        Assert.assertFalse(scheduleUtils.isWithinWindow(outOfWindowOvernightRange))

        // Switch to noon for isWithinSameDay ranges
        Mockito.`when`(mockTimeUtil.systemZonedNow()).thenReturn(NOON_UTC)

        val inWindowSameDayRange = IntRange(ONE_AM, ONE_PM)
        Assert.assertTrue(scheduleUtils.isWithinWindow(inWindowSameDayRange))

        val outOfWindowSameDayRange = IntRange(ONE_PM, TWO_PM)
        Assert.assertFalse(scheduleUtils.isWithinWindow(outOfWindowSameDayRange))
    }

    @Test
    fun `test findNextInstallTime is before start in same day`() {
        // Midnight should schedule at 10 am of same day
        val sameDayRange = IntRange(TEN_AM, ONE_PM)
        val nextInstallMillis = scheduleUtils.findNextInstallTimeMillis(sameDayRange)

        val expected = ZonedDateTime.of(2000, 1, 1, TEN_AM, 0, 0, 0, ZoneOffset.UTC)
        val expectedMillis = expected.toInstant().toEpochMilli()
        Assert.assertEquals(expectedMillis, nextInstallMillis)
    }

    @Test
    fun `test findNextInstallTime is after end in same day`() {
        Mockito.`when`(mockTimeUtil.systemZonedNow()).thenReturn(NOON_UTC)

        // Midnight should schedule at 10 am of same day
        val sameDayRange = IntRange(ONE_AM, TEN_AM)
        val nextInstallMillis = scheduleUtils.findNextInstallTimeMillis(sameDayRange)

        val expected = ZonedDateTime.of(2000, 1, 1, ONE_AM, 0, 0, 0, ZoneOffset.UTC)
            // Expected plus days!
            .plusDays(1)

        val expectedMillis = expected.toInstant().toEpochMilli()
        Assert.assertEquals(expectedMillis, nextInstallMillis)
    }

    @Test
    fun `test findNextInstallTime is one second if in window`() {
        Mockito.`when`(mockTimeUtil.systemZonedNow()).thenReturn(NOON_UTC)

        val inWindowDelays = arrayOf(1000L, 3 * 1000L, 30 * 1000L, 60 * 1000L, 5 * 60 * 1000L)

        // Midnight should schedule at 10 am of same day
        val sameDayRange = IntRange(ONE_AM, ONE_PM)

        inWindowDelays.forEach { delayMillis ->
            val nextInstallMillis = scheduleUtils.findNextInstallTimeMillis(
                installWindow = sameDayRange,
                withinWindowDelay = delayMillis
            )

            val expected = NOON_UTC
                // Expected plus millis!
                .plusNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis))

            val expectedMillis = expected.toInstant().toEpochMilli()
            Assert.assertEquals(expectedMillis, nextInstallMillis)
        }
    }

    @Test
    fun `test findNextDownloadTimeMillis works within threshold`() {
        val initial = 3L // Seconds
        val threshold = 3L

        val results = (1..threshold).map { attempt ->
            scheduleUtils.findNextDownloadTimeMillis(
                attempts = attempt.toInt(),
                initialRetrySeconds = initial,
                retryThreshold = threshold
            )
        }

        val expectedAttemptOne = MIDNIGHT_UTC.plusSeconds(initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptOne, results[0])

        val expectedAttemptTwo = MIDNIGHT_UTC.plusSeconds(initial * initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptTwo, results[1])

        val expectedAttemptThree = MIDNIGHT_UTC.plusSeconds(initial * initial * initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptThree, results[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test findNextDownloadTimeMillis throws on zero attempts`() {
        scheduleUtils.findNextDownloadTimeMillis(
            attempts = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test findNextDownloadTimeMillis throws on negative attempts`() {
        scheduleUtils.findNextDownloadTimeMillis(
            attempts = -1
        )
    }

    @Test
    fun `test findNextDownloadTimeMillis restarts after threshold`() {
        val initial = 3L // Seconds
        val threshold = 3L

        val afterThreshold = threshold + 1
        val newEnd = afterThreshold + threshold

        // Shift from 1..3 to 3..6
        val results = (afterThreshold..newEnd).map { attempt ->
            scheduleUtils.findNextDownloadTimeMillis(
                attempts = attempt.toInt(),
                initialRetrySeconds = initial,
                retryThreshold = threshold
            )
        }

        val expectedAttemptOne = MIDNIGHT_UTC.plusSeconds(initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptOne, results[0])

        val expectedAttemptTwo = MIDNIGHT_UTC.plusSeconds(initial * initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptTwo, results[1])

        val expectedAttemptThree = MIDNIGHT_UTC.plusSeconds(initial * initial * initial).toInstant().toEpochMilli()
        Assert.assertEquals(expectedAttemptThree, results[2])
    }
}