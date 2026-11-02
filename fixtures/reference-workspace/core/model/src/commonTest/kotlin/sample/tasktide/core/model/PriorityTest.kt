package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PriorityTest {
    @Test
    fun levelRoundTrips() {
        Priority.entries.forEach { assertEquals(it, Priority.fromLevel(it.level)) }
    }

    @Test
    fun unknownLevelFallsBackToNormal() {
        assertEquals(Priority.NORMAL, Priority.fromLevel(42))
    }
}
