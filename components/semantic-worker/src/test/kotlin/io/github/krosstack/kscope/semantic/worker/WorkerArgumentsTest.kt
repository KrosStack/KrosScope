package io.github.krosstack.kscope.semantic.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkerArgumentsTest {
    @Test
    fun `device defaults to cpu`() {
        assertEquals("cpu", WorkerArguments(arrayOf("model")).device)
    }

    @Test
    fun `cuda can be selected explicitly`() {
        assertEquals("cuda", WorkerArguments(arrayOf("model", "cuda")).device)
    }

    @Test
    fun `invalid arguments are rejected`() {
        listOf(emptyArray(), arrayOf("model", "gpu"), arrayOf("model", "cpu", "extra")).forEach {
            assertFailsWith<IllegalArgumentException> { WorkerArguments(it) }
        }
    }
}
