package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.worker.WorkerFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val HASH_LENGTH = 64

class BatchFactsTest {
    private val shapes = SampleWorkspace.singleModule("shapes", "shapes/main")
    private val overloads = SampleWorkspace.singleModule("overloads", "overloads/main")

    private fun FactBatch.qualifiedNames(): Set<String> = listOf(this).symbols().mapNotNull { it.qualifiedName }.toSet()

    @Test
    fun `a file with syntax errors yields its declarations and a warning`() {
        val batches = analyze(SampleWorkspace.singleModule("broken", "broken/main"))
        val names = batches.symbols().mapNotNull { it.qualifiedName }
        val expected = listOf("broken.beforeTheError", "broken.Damaged.afterTheError", "broken.trailing")
        assertTrue(names.containsAll(expected))
        val diagnostic = batches.diagnostics().single()
        assertEquals("kotlin.syntax-error", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals(8, diagnostic.location?.range?.startLine)
    }

    @Test
    fun `a requested file that does not exist is reported and does not abort the batch`() {
        val ghost = WorkerFile(
            ArtifactRef(RepositoryId("sample"), RepoPath("shapes/main/kotlin/shapes/Ghost.kt")),
            "/nowhere/Ghost.kt",
            ContentHash("0".repeat(HASH_LENGTH)),
        )
        val source = shapes.sourceSets.single()
        val withGhost = shapes.copy(sourceSets = listOf(source.copy(files = source.files + ghost)))
        val batches = analyze(withGhost)
        assertTrue(batches.symbols().isNotEmpty())
        val failure = batches.diagnostics().single()
        assertEquals("kotlin.file-analysis-failed", failure.code.value)
        assertEquals(DiagnosticSeverity.ERROR, failure.severity)
    }

    @Test
    fun `a file whose declarations cannot be identified yields an error and the batch is still produced`() {
        val padded = SampleWorkspace.sourceSet("bad", " padded", "shapes/main")
        val module = SampleWorkspace.module("bad", Platform.JVM, padded)
        val batch = analyze(module).single()
        val failure = listOf(batch).diagnostics().single()
        assertEquals("kotlin.file-analysis-failed", failure.code.value)
        assertEquals(DiagnosticSeverity.ERROR, failure.severity)
        assertTrue(listOf(batch).symbols().isEmpty())
    }

    @Test
    fun `a batch is produced only for source sets that ask for facts and echoes their fingerprint`() {
        val context = SampleWorkspace.sourceSet("lib", "commonMain", "kmp/commonMain", emitFactsFor = false)
        val emitting = SampleWorkspace.sourceSet("lib", "jvmMain", "kmp/jvmMain")
        val common = SampleWorkspace.module("common", Platform.COMMON, context)
        val jvm = SampleWorkspace.module("jvm", Platform.JVM, emitting).copy(dependsOn = listOf("common"))
        val batches = analyze(common, jvm)
        val batch = batches.single()
        assertEquals(OwnershipKind.SOURCE_SET, batch.owner.kind)
        assertEquals(emitting.sourceSet.value, batch.owner.key)
        assertEquals(emitting.fingerprint, batch.inputFingerprint)
        assertEquals("kscope.analyzer.kotlin", batch.producer.id)
        assertNotNull(batch.producer.version)
        assertTrue(batches.symbols().none { SymbolFlag.EXPECT in it.flags })
    }

    @Test
    fun `references to unknown modules are reported as warnings on the module's batches`() {
        val diagnostic = analyze(shapes.copy(dependencies = listOf("missing"))).diagnostics().single()
        assertEquals("kotlin.unknown-module-reference", diagnostic.code.value)
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
    }

    @Test
    fun `symbols of a merged module are owned by the source set of their file`() {
        val module = SampleWorkspace.module(
            "app:main+debug",
            Platform.ANDROID,
            SampleWorkspace.sourceSet("app", "main", "merged/main"),
            SampleWorkspace.sourceSet("app", "debug", "merged/debug"),
        )
        val bySourceSet = analyze(module).associate { batch -> batch.owner.key to batch.qualifiedNames() }
        assertEquals(setOf("app.MainScreen", "app.MainScreen.render", "app.shared"), bySourceSet["sample//:app@main"])
        val debug = setOf("app.DebugOverlay", "app.DebugOverlay.show", "app.debugOnly", "app.shared")
        assertEquals(debug, bySourceSet["sample//:app@debug"])
    }

    @Test
    fun `analysing the same input twice gives identical bytes`() {
        val first = CanonicalFacts.toJson(analyze(shapes, overloads))
        val second = CanonicalFacts.toJson(analyze(shapes, overloads))
        assertEquals(first, second)
    }

    @Test
    fun `the order of modules in the request does not change the output`() {
        val forward = CanonicalFacts.toJson(analyze(shapes, overloads))
        val backward = CanonicalFacts.toJson(analyze(overloads, shapes))
        assertEquals(forward, backward)
    }
}
