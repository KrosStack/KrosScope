package io.github.krosstack.kscope.analyzer.kotlin.worker.declare

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.WorkerIdentity
import io.github.krosstack.kscope.analyzer.kotlin.worker.aa.ScriptParser
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyze
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyntaxDeclarationsTest {
    private fun declare(vararg modules: WorkerModule): List<FactBatch> =
        ScriptParser().use { SyntaxFactPass(WorkerIdentity.producer, it).batchSequence(modules.toList()).toList() }

    private fun module(name: String, directory: String) = SampleWorkspace.singleModule(name, directory)

    private fun Symbol.shape() = listOf(id.value, kind.id, qualifiedName, signature, visibility, flags, declaration)

    private fun List<FactBatch>.shapes() = symbols().map { it.shape() }

    @Test
    fun `classes, functions, properties and type aliases are read without resolution`() {
        val symbols = declare(module("shapes", "shapes/main")).symbols()
        val kinds = symbols.map { it.kind.id }.toSet()
        assertTrue(kinds.containsAll(listOf("kotlin.class", "kotlin.function", "kotlin.property")), kinds.toString())
        assertTrue(symbols.isNotEmpty())
    }

    @Test
    fun `type aliases and expect and actual markers are read`() {
        val common = SampleWorkspace.module(
            "lib:commonMain",
            Platform.COMMON,
            SampleWorkspace.sourceSet("lib", "commonMain", "kmp/commonMain"),
        )
        val jvm = SampleWorkspace.module(
            "lib:jvmMain",
            Platform.JVM,
            SampleWorkspace.sourceSet("lib", "jvmMain", "kmp/jvmMain"),
        ).copy(dependsOn = listOf("lib:commonMain"))
        val syntax = declare(common, jvm)
        assertEquals(analyze(common, jvm).shapes(), syntax.shapes())
        val flags = syntax.symbols().flatMap { it.flags }.map { it.name }.toSet()
        assertTrue("EXPECT" in flags && "ACTUAL" in flags, flags.toString())
    }

    @Test
    fun `kotlin and java declarations equal the resolved ones apart from annotation names`() {
        listOf("shapes" to "shapes/main", "mixed" to "mixed/main", "visibility" to "visibility/main").forEach {
            val module = module(it.first, it.second)
            assertEquals(analyze(module).shapes(), declare(module).shapes(), it.first)
        }
    }

    @Test
    fun `every source set carries one note that references were not analysed and no relationship`() {
        val batches = declare(module("mixed", "mixed/main"))
        batches.forEach { batch ->
            val notes = listOf(batch).diagnostics().filter { it.code.value == NOT_ANALYSED }
            assertEquals(1, notes.size)
            assertEquals(DiagnosticSeverity.INFO, notes.single().severity)
        }
        assertEquals(emptyList(), batches.relationships())
    }

    @Test
    fun `a file that does not parse gives the diagnostics of the resolved pass`() {
        val module = module("broken", "broken/main")
        val syntax = declare(module).diagnostics().filterNot { it.code.value == NOT_ANALYSED }
        assertTrue(syntax.isNotEmpty())
        assertEquals(analyze(module).diagnostics(), syntax)
    }

    @Test
    fun `batches are deterministic and keep the fingerprint of the source set`() {
        val module = module("mixed", "mixed/main")
        val first = declare(module)
        assertEquals(first, declare(module))
        assertEquals(module.sourceSets.map { it.fingerprint }, first.map { it.inputFingerprint })
    }

    @Test
    fun `source sets that do not ask for facts are not answered`() {
        val module = module("mixed", "mixed/main")
        val silent = module.copy(sourceSets = module.sourceSets.map { it.copy(emitFactsFor = false) })
        assertEquals(emptyList(), declare(silent))
    }
}
