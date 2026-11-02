package sample.tasktide.core.model

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDatesZoneTest {
    private val original = TimeZone.getDefault()

    @AfterTest
    fun restoreZone() {
        TimeZone.setDefault(original)
    }

    private fun useZone(id: String) = TimeZone.setDefault(TimeZone.getTimeZone(id))

    @Test
    fun utcDeviceKeepsTheUtcCalendarDay() {
        useZone("UTC")

        assertEquals("1970-01-01", 0L.toIsoDate())
        assertEquals("2023-11-14", 1_700_000_000_000L.toIsoDate())
        assertEquals("2024-02-29", 1_709_164_800_000L.toIsoDate())
    }

    @Test
    fun deviceAheadOfUtcMovesLateEveningsToTheNextDay() {
        useZone("GMT+14:00")

        assertEquals("1970-01-01", 0L.toIsoDate())
        assertEquals("1970-01-01", 35_999_999L.toIsoDate())
        assertEquals("1970-01-02", 36_000_000L.toIsoDate())
        assertEquals("1970-01-02", 86_399_999L.toIsoDate())
    }

    @Test
    fun deviceBehindUtcMovesEarlyMorningsToThePreviousDay() {
        useZone("GMT-11:00")

        assertEquals("1969-12-31", 0L.toIsoDate())
        assertEquals("1969-12-31", 39_599_999L.toIsoDate())
        assertEquals("1970-01-01", 39_600_000L.toIsoDate())
    }

    @Test
    fun offsetsThatAreNotWholeHoursAreHonoured() {
        useZone("GMT+05:30")

        assertEquals("1970-01-01", 66_599_999L.toIsoDate())
        assertEquals("1970-01-02", 66_600_000L.toIsoDate())
    }

    @Test
    fun theOffsetFollowsTheCurrentDeviceZone() {
        useZone("GMT+10:00")
        val east = 82_800_000L.toIsoDate()
        useZone("GMT-10:00")
        val west = 82_800_000L.toIsoDate()

        assertEquals("1970-01-02", east)
        assertEquals("1970-01-01", west)
    }

    @Test
    fun leapDaysAreFormattedInTheDeviceZone() {
        useZone("GMT+02:00")

        assertEquals("2024-02-29", 1_709_164_800_000L.toIsoDate())
        assertEquals("2024-03-01", 1_709_251_199_000L.toIsoDate())
    }
}
