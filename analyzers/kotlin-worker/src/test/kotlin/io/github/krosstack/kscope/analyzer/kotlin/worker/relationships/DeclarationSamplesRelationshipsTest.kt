package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sample trees of the declaration pass hold every declaration shape; the resolution pass must cope with them. */
class DeclarationSamplesRelationshipsTest {
    private fun single(name: String, directory: String) = SampleWorkspace.singleModule(name, directory)

    private fun assertCompiles(modules: List<WorkerModule>, synthesized: (String) -> Boolean = { false }) {
        val batches = analyzeAll(modules) { synthesized(it.value) }
        val failures = batches.diagnostics().filter {
            it.severity == DiagnosticSeverity.ERROR || it.code.value == "kotlin.reference-resolution-failed"
        }
        assertEquals(emptyList(), failures.map { it.message })
    }

    @Test
    fun `declaration shapes overloads constructors flags and documentation resolve without failures`() {
        assertCompiles(
            listOf(
                single("shapes", "shapes/main"),
                single("overloads", "overloads/main"),
                single("constructors", "constructors/main"),
                single("flags", "flags/main"),
                single("docs", "docs/main"),
            ),
        )
    }

    @Test
    fun `tests with annotations from libraries and the mixed sample resolve without failures`() {
        assertCompiles(listOf(single("tests", "tests/test"), single("mixed", "mixed/main")))
    }

    @Test
    fun `a module that uses a generated resource class resolves it through its stub`() {
        val module = SampleWorkspace.module(
            "gen",
            Platform.ANDROID,
            SampleWorkspace.sourceSet(
                "gen",
                "main",
                "generated/main",
                syntheticSources = listOf(SampleWorkspace.androidResourceStub()),
            ),
        )
        assertCompiles(listOf(module)) { " gen/R#" in it }
    }

    @Test
    fun `a file with syntax errors from the declaration samples resolves without failures`() {
        assertCompiles(listOf(single("broken", "broken/main")))
    }

    @Test
    fun `multiplatform declaration samples resolve without failures`() {
        val common = SampleWorkspace.sourceSet("lib", "commonMain", "kmp/commonMain")
        val jvm = SampleWorkspace.sourceSet("lib", "jvmMain", "kmp/jvmMain")
        assertCompiles(
            listOf(
                SampleWorkspace.module("lib:commonMain", Platform.COMMON, common),
                SampleWorkspace.module("lib:jvmMain", Platform.JVM, jvm).copy(dependsOn = listOf("lib:commonMain")),
            ),
        )
    }
}
