package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDatesTest {
    @Test
    fun epochIsFormattedAsFirstDayOf1970() {
        assertEquals("1970-01-01", 0L.toIsoDate())
    }

    @Test
    fun knownTimestampIsFormatted() {
        assertEquals("2023-11-14", 1_700_000_000_000L.toIsoDate())
    }

    @Test
    fun leapDayIsFormatted() {
        assertEquals("2024-02-29", 1_709_164_800_000L.toIsoDate())
    }
}
