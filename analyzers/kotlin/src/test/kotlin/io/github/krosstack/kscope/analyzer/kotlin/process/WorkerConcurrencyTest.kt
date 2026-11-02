package io.github.krosstack.kscope.analyzer.kotlin.process

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkerConcurrencyTest {
    private val gigabyte = 1L shl 30

    private fun workers(
        environment: Map<String, String> = emptyMap(),
        processors: Int = 16,
        memory: Long = 64 * gigabyte,
    ) = WorkerConcurrency.resolve(environment::get, processors, { memory }).workers

    @Test
    fun `the variable sets the number of workers`() {
        assertEquals(5, workers(mapOf(WorkerConcurrency.VARIABLE to "5"), processors = 2, memory = gigabyte))
    }

    @Test
    fun `a value that is not a positive number is ignored`() {
        val derived = workers()
        for (value in listOf("0", "-2", "many", "")) {
            assertEquals(derived, workers(mapOf(WorkerConcurrency.VARIABLE to value)), value)
        }
    }

    @Test
    fun `a worker is allowed for every two processors up to eight`() {
        assertEquals(8, workers(processors = 32))
        assertEquals(4, workers(processors = 8))
        assertEquals(1, workers(processors = 1))
    }

    @Test
    fun `a worker is allowed for every heap and native overhead that memory holds beyond the client reserve`() {
        assertEquals(3, workers(memory = 12 * gigabyte))
        assertEquals(1, workers(memory = 5 * gigabyte))
        assertEquals(1, workers(memory = gigabyte / 2))
        assertEquals(2, workers(mapOf(WorkerLocator.HEAP_VARIABLE to "4g"), memory = 14 * gigabyte))
    }

    @Test
    fun `heap sizes are read as the java option reads them`() {
        assertEquals(4L shl 30, HeapSize.bytesOf("4g"))
        assertEquals(512L shl 20, HeapSize.bytesOf("512M"))
        assertEquals(64L shl 10, HeapSize.bytesOf("64k"))
        assertEquals(1000L, HeapSize.bytesOf("1000"))
        assertEquals(2L shl 30, HeapSize.bytesOf("a lot"))
    }
}
