package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.AnsweringLinker
import io.github.krosstack.kscope.engine.FAKE_NAMESPACE
import io.github.krosstack.kscope.engine.ReferencingProducer
import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.SloppyLinker
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.engine.store
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.answerKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolicReferenceLinkingTest {
    private val mainFile = "app/src/main/kotlin/com/example/app/Main.kt"
    private val appTest = "app/src/test/kotlin/com/example/app/MainTest.kt"
    private val greeter = "core/src/commonMain/kotlin/com/example/core/Greeter.kt"
    private val instrumented = "app/src/androidTest/kotlin/com/example/app/MainInstrumentedTest.kt"

    private fun ScanEngine.references(namespace: String? = null): List<SymbolicReference> =
        graph().symbolicReferences(namespace)

    private fun ScanEngine.linkerRelationships(): List<Relationship> =
        store.batches().filter { it.producer.id == "linker" }.flatMap { it.facts }
            .filterIsInstance<Fact.OfRelationship>().map { it.value }

    private fun TestWorkspace.keys() {
        write(mainFile, "found-main\n")
        write(appTest, "missing-test\n")
        write(greeter, "found-greeter\n")
        write(instrumented, "elsewhere\n")
    }

    @Test
    fun `every symbolic reference of a claimed namespace ends in exactly one relationship of some state`() {
        val registry = coreRegistry(ReferencingProducer("referencing"), AnsweringLinker("linker"))
        scanningSample(registry) { workspace, engine ->
            workspace.keys()
            val report = engine.update()

            val references = engine.references(FAKE_NAMESPACE)
            val answers = engine.linkerRelationships()
            assertEquals(6, references.size)
            assertEquals(
                references.map { it.answerKey() }.sortedBy { it.toString() },
                answers.map { it.answerKey() }.sortedBy { it.toString() },
            )
            assertEquals(2, answers.count { it.resolution is Resolution.Resolved })
            assertEquals(1, answers.count { it.resolution is Resolution.Unresolved })
            assertEquals(3, answers.count { it.resolution is Resolution.NotAnalysed })
            assertTrue(report.diagnostics.none { it.code.value.startsWith("engine.symbolic-reference") })
        }
    }

    @Test
    fun `linker output is replaced on every change and never accumulates`() {
        val linker = AnsweringLinker("linker")
        scanningSample(coreRegistry(ReferencingProducer("referencing"), linker)) { workspace, engine ->
            workspace.keys()
            engine.update()
            val before = engine.linkerRelationships()

            workspace.write(mainFile, "missing-now\n")
            engine.update()
            val after = engine.linkerRelationships()

            assertEquals(2, linker.linkCalls)
            assertEquals(before.size, after.size)
            val main = after.single { it.site?.artifact?.path?.value == mainFile }
            assertTrue(main.resolution is Resolution.Unresolved)
            workspace.delete(instrumented)
            engine.update()
            assertEquals(5, engine.linkerRelationships().size)
            engine.assertMatchesRebuild()
        }
    }

    @Test
    fun `a linker is not run again while nothing changed`() {
        val linker = AnsweringLinker("linker")
        scanningSample(coreRegistry(ReferencingProducer("referencing"), linker)) { workspace, engine ->
            workspace.keys()
            engine.update()
            engine.update()
            assertEquals(1, linker.linkCalls)
        }
    }

    @Test
    fun `a linker that leaves a reference unanswered or answers it twice is reported`() {
        scanningSample(coreRegistry(ReferencingProducer("referencing"), SloppyLinker("linker"))) { workspace, engine ->
            workspace.write(mainFile, "skip-main\n")
            workspace.write(greeter, "twice-greeter\n")
            val report = engine.update()

            val codes = report.diagnostics.filter { it.code.value.startsWith("engine.symbolic-reference") }
            assertEquals(
                listOf("engine.symbolic-reference-duplicated", "engine.symbolic-reference-unlinked"),
                codes.map { it.code.value }.sorted(),
            )
            assertTrue(codes.all { it.severity.name == "ERROR" })
            assertTrue(codes.single { it.code.value.endsWith("unlinked") }.message.contains("skip-main"))
            assertTrue(codes.single { it.code.value.endsWith("duplicated") }.message.contains("twice-greeter"))
        }
    }

    @Test
    fun `references of a namespace that no linker claims are not audited`() {
        val registry = coreRegistry(ReferencingProducer("referencing", "other.ns"), AnsweringLinker("linker"))
        scanningSample(registry) { workspace, engine ->
            workspace.keys()
            val report = engine.update()
            assertEquals(6, engine.references("other.ns").size)
            assertEquals(emptyList(), engine.linkerRelationships())
            assertTrue(report.diagnostics.none { it.code.value.startsWith("engine.symbolic-reference") })
        }
    }

    @Test
    fun `references of removed units disappear with them`() {
        val registry = coreRegistry(ReferencingProducer("referencing"), AnsweringLinker("linker"))
        TestWorkspace("gradle-multi").use { workspace ->
            workspace.keys()
            workspace.engine(registry).use { it.update() }
            workspace.engine().use { engine ->
                engine.update()
                assertEquals(emptyList(), engine.references())
            }
        }
    }
}
