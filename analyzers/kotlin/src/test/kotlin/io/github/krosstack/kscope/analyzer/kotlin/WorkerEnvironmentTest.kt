package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.process.HeapSize
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLocator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkerEnvironmentTest {
    private fun workers(value: String?) = WorkerEnvironment.workers(mapOf("KSCOPE_WORKERS" to value)::get)

    private fun heap(value: String?) = WorkerEnvironment.heapMebibytes(mapOf("KSCOPE_WORKER_HEAP" to value)::get)

    @Test
    fun `the variables are the documented ones`() {
        assertEquals("KSCOPE_WORKERS", WorkerEnvironment.WORKERS_VARIABLE)
        assertEquals("KSCOPE_WORKER_HEAP", WorkerEnvironment.HEAP_VARIABLE)
    }

    @Test
    fun `a number of workers is a whole number of at least one and at most sixty four`() {
        assertEquals(1, workers("1"))
        assertEquals(5, workers(" 5 "))
        assertEquals(64, workers("64"))
        assertEquals(64, workers("1000"))
        listOf("0", "-1", "many", "", " ", "2.5", "99999999999").forEach { assertNull(workers(it), it) }
        assertNull(workers(null))
    }

    @Test
    fun `a heap is digits with an optional unit and is read in mebibytes rounded up`() {
        assertEquals(4096, heap("4g"))
        assertEquals(512, heap("512m"))
        assertEquals(512, heap(" 512M "))
        assertEquals(1, heap("1024k"))
        assertEquals(2, heap("1025k"))
        val unusable = listOf("0", "-1g", "a lot", "", " ", "1.5g", "12x", "99999999999999999999g")
        unusable.forEach { assertNull(heap(it), it) }
        assertNull(heap(null))
    }

    @Test
    fun `the default heap is the two gigabytes that the worker starts with`() {
        assertEquals(HeapSize.mebibytesOf("2g"), WorkerEnvironment.DEFAULT_HEAP_MEBIBYTES)
        assertEquals("2g", WorkerLocator.heapSetting(emptyMap<String, String>()::get))
    }

    @Test
    fun `settings accept no number that is not a count or a size`() {
        assertFailsWith<IllegalArgumentException> { WorkerSettings(workers = 0) }
        assertFailsWith<IllegalArgumentException> { WorkerSettings(heapMebibytes = 0) }
        assertNull(WorkerSettings.NONE.workers)
        assertNull(WorkerSettings.NONE.heapMebibytes)
    }

    @Test
    fun `the settings are asked only when workers start`() {
        val asked = AtomicInteger()
        val settings = { asked.incrementAndGet().let { WorkerSettings.NONE } }

        KotlinAnalyzerProducer(settings, workerOutput = {})
        KotlinApiDigester(settings, workerOutput = {})
        KotlinScriptOutliner(settings, workerOutput = {})

        assertEquals(0, asked.get())
    }
}
