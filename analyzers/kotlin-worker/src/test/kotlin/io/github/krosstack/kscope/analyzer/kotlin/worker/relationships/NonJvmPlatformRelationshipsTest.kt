package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source sets of a platform without a JVM resolve their references although the standard library the worker offers
 * them is the JVM one: its collection aliases expand to Java classes, whose scopes a session without Java support
 * cannot build.
 */
class NonJvmPlatformRelationshipsTest {
    private val describe = "rel/js/describe(Totals)."
    private val tally = "rel/js/tally(List)."

    private fun module(platform: Platform): WorkerModule = SampleWorkspace.module(
        "web",
        platform,
        SampleWorkspace.sourceSet("web", "jsMain", "relations/js/jsMain"),
    )

    private fun failuresOn(platform: Platform): List<String> {
        val failureCodes = setOf("kotlin.reference-resolution-failed", "kotlin.file-analysis-failed")
        return analyzeAll(module(platform)).diagnostics().filter { it.code.value in failureCodes }.map { it.message }
    }

    private fun edgesOn(platform: Platform) = Edges("web@jsMain ").of(analyzeAll(module(platform)))

    @Test
    fun `a JavaScript source set that uses collection aliases loses no reference to a compiler failure`() {
        assertEquals(emptyList(), failuresOn(Platform.JS))
    }

    @Test
    fun `the references after a collection alias in a JavaScript source set are still resolved`() {
        Edges.assertHas(
            edgesOn(Platform.JS),
            "$describe references/read -> rel/js/Totals#bySession.",
            "$describe references/read -> rel/js/Totals#sum.",
            "$tally references/read -> rel/js/Vote#sessionId.",
            "$tally references/read -> rel/js/Vote#score.",
        )
    }

    @Test
    fun `native and wasm source sets resolve exactly what a JavaScript one resolves`() {
        val expected = edgesOn(Platform.JS).sorted()
        assertTrue(expected.isNotEmpty())
        listOf(Platform.NATIVE, Platform.WASM).forEach { platform ->
            assertEquals(emptyList(), failuresOn(platform), "failures on $platform")
            assertEquals(expected, edgesOn(platform).sorted(), "edges on $platform")
        }
    }
}
