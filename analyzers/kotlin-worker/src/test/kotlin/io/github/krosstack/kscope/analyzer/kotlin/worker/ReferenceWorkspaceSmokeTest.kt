package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the whole analysis over the real sources of the reference workspace in a handcrafted module layout, with the
 * standard library and the JDK as the only libraries, and prints what it found.
 */
class ReferenceWorkspaceSmokeTest {
    private class HeapSampler : Thread("heap-sampler") {
        @Volatile
        var peak = 0L

        @Volatile
        private var running = true

        init {
            isDaemon = true
        }

        override fun run() {
            val runtime = Runtime.getRuntime()
            while (running) {
                peak = maxOf(peak, runtime.totalMemory() - runtime.freeMemory())
                runCatching { sleep(SAMPLE_MILLIS) }
            }
        }

        fun finish(): Long {
            running = false
            return peak
        }
    }

    @Test
    fun `the whole analysis reads every source file of the reference workspace without failures`() {
        val modules = ReferenceWorkspaceLayout.modules()
        val fileCount = ReferenceWorkspaceLayout.fileCount()
        val sampler = HeapSampler().also { it.start() }
        val started = System.nanoTime()
        val batches = analyzeAll(modules)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        val peakMegabytes = sampler.finish() / BYTES_PER_MEGABYTE

        val failures = batches.diagnostics().filter { it.severity == DiagnosticSeverity.ERROR }
        assertEquals(emptyList(), failures)
        assertEquals(modules.size, batches.size)
        val relationships = batches.relationships()
        report("${modules.size} source sets, $fileCount files, ${batches.symbols().size} symbols")
        report("${relationships.size} relationships in $millis ms (${millis / fileCount} ms per file)")
        report("peak heap $peakMegabytes MB")
        relationships.groupingBy { it.kind.name.lowercase() }.eachCount().toSortedMap()
            .forEach { (kind, count) -> report("relationships $kind=$count") }
        val resolutions = relationships.map { it.resolution }
        resolutions.filterIsInstance<Resolution.Resolved>().groupingBy { it.certainty.name.lowercase() }.eachCount()
            .toSortedMap().forEach { (certainty, count) -> report("resolved $certainty=$count") }
        resolutions.filterIsInstance<Resolution.Unresolved>().groupingBy { it.reason.value }.eachCount().toSortedMap()
            .forEach { (reason, count) -> report("unresolved $reason=$count") }
        batches.diagnostics().groupingBy { it.code.value }.eachCount().toSortedMap()
            .forEach { (code, count) -> report("diagnostic $code=$count") }
        assertTrue(relationships.size > MINIMUM_RELATIONSHIPS)
    }

    private fun report(line: String) = println("reference workspace: $line")

    private companion object {
        const val MINIMUM_RELATIONSHIPS = 1000
        const val SAMPLE_MILLIS = 20L
        const val NANOS_PER_MILLI = 1_000_000L
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
