package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.OpenOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenOptionsTest {
    @Test
    fun `a worker memory below one is refused`() {
        for (value in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertFailsWith<IllegalArgumentException> { OpenOptions(workerMemoryMib = value) }
        }
        assertEquals(1L, OpenOptions(workerMemoryMib = 1).workerMemoryMib)
        assertEquals(null, OpenOptions().workerMemoryMib)
    }
}
