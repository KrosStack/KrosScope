package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.WorkerSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/** The number of workers when a settings file sets it: the variable, then the file, then what the machine holds. */
class WorkerSettingsConcurrencyTest {
    private val gigabyte = 1L shl 30

    private fun workers(
        environment: Map<String, String> = emptyMap(),
        processors: Int = 16,
        memory: Long = 64 * gigabyte,
        settings: WorkerSettings = WorkerSettings.NONE,
    ) = WorkerConcurrency.resolve(environment::get, processors, { memory }, settings).workers

    @Test
    fun `the workers of the settings apply when the variable is not set`() {
        assertEquals(3, workers(settings = WorkerSettings(workers = 3)))
        assertEquals(3, workers(processors = 2, settings = WorkerSettings(workers = 3)))
    }

    @Test
    fun `the workers of the settings are limited to what the memory holds`() {
        val settings = WorkerSettings(workers = 8)
        assertEquals(3, workers(memory = 12 * gigabyte, settings = settings))
        assertEquals(1, workers(memory = gigabyte, settings = settings))
        assertEquals(8, workers(memory = 64 * gigabyte, settings = settings))
    }

    @Test
    fun `the heap of the settings counts in the memory a worker needs`() {
        val settings = WorkerSettings(heapMebibytes = 4096)
        assertEquals(2, workers(memory = 14 * gigabyte, settings = settings))
        assertEquals(2, workers(memory = 14 * gigabyte, settings = WorkerSettings(workers = 8, heapMebibytes = 4096)))
    }

    @Test
    fun `the variable beats the settings and is not limited by memory`() {
        val settings = WorkerSettings(workers = 3)
        assertEquals(5, workers(mapOf(WorkerConcurrency.VARIABLE to "5"), settings = settings))
        assertEquals(6, workers(mapOf(WorkerConcurrency.VARIABLE to "6"), memory = gigabyte, settings = settings))
    }

    @Test
    fun `a variable that is not usable falls through to the settings`() {
        val settings = WorkerSettings(workers = 3)
        for (value in listOf("0", "-2", "many", "", " ")) {
            assertEquals(3, workers(mapOf(WorkerConcurrency.VARIABLE to value), settings = settings), value)
        }
    }

    @Test
    fun `the heap variable beats the heap of the settings in the memory a worker needs`() {
        val settings = WorkerSettings(heapMebibytes = 8192)
        val environment = mapOf(WorkerLocator.HEAP_VARIABLE to "2g")
        assertEquals(3, workers(environment, memory = 12 * gigabyte, settings = settings))
        assertEquals(1, workers(memory = 12 * gigabyte, settings = settings))
    }

    @Test
    fun `without settings the number is derived as it always was`() {
        assertEquals(8, workers(processors = 32))
        assertEquals(3, workers(memory = 12 * gigabyte))
        assertEquals(5, workers(mapOf(WorkerConcurrency.VARIABLE to "5")))
    }
}
