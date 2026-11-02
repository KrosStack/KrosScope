package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IsoDatesTest {
    private val isoDate = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

    @Test
    fun formattedDatesHaveTheIsoShape() {
        listOf(0L, 1_700_000_000_000L, 1_709_164_800_000L).forEach { millis ->
            assertTrue(isoDate.matches(millis.toIsoDate()), millis.toString())
        }
    }

    @Test
    fun timestampsTwoDaysApartAreOnDifferentDays() {
        assertNotEquals(0L.toIsoDate(), 172_800_000L.toIsoDate())
    }

    @Test
    fun theSameInstantAlwaysGivesTheSameText() {
        assertEquals(1_700_000_000_000L.toIsoDate(), 1_700_000_000_000L.toIsoDate())
    }
}
