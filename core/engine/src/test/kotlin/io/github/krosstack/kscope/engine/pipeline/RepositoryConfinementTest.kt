package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.TwoRepositoryWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.descriptorOf
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.engine.scanningSample
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.ApiDigester
import io.github.krosstack.kscope.spi.ApiDigests
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class Context(
    val units: Set<RepositoryId>,
    val model: Set<RepositoryId>,
    val artifacts: Set<RepositoryId>,
    val modules: Int,
)

/** Every source has the same API, so editing a body makes only the units of its own source set stale. */
private class ConstantApi : ApiDigester {
    override val version = "1"
    override val consumes = setOf(kotlinSource)

    override fun digest(sources: List<Artifact>, files: WorkspaceFiles) =
        ApiDigests(sources.associate { it.ref to "api" }, emptySet())
}

/** Answers each unit with the number of modules it was given, so that its facts show what the unit could see. */
private class ContextRecorder : FactProducer {
    override val descriptor = descriptorOf("context-recorder", kotlinSource, OwnershipKind.SOURCE_SET)
    override val apiDigester = ConstantApi()
    val contexts = mutableListOf<Context>()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        contexts += Context(
            units = request.units.flatMap { unit -> unit.artifacts.map { it.ref.repository } }.toSet(),
            model = request.model.buildRoots.map { it.repository }.toSet(),
            artifacts = request.artifacts.map { it.ref.repository }.toSet(),
            modules = request.model.modules.size,
        )
        request.units.forEach { unit ->
            val message = "${unit.owner.key} sees ${request.model.modules.size} modules"
            val note = Diagnostic(DiagnosticSeverity.INFO, ReasonCode("fake.context"), message, null)
            val facts = listOf<Fact>(Fact.OfDiagnostic(note))
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint("ignored"), facts))
        }
    }
}

class RepositoryConfinementTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `without dependency edges the source sets of one repository never see the model or files of another`() =
        TwoRepositoryWorkspace().use { workspace ->
            val recorder = ContextRecorder()
            workspace.engine(coreRegistry(recorder)).use { engine ->
                engine.update()
                assertEquals(2, recorder.contexts.size)
                recorder.contexts.forEach { context ->
                    assertEquals(1, context.units.size)
                    assertEquals(context.units, context.model)
                    assertTrue(context.artifacts.all { it in context.units })
                }
                assertEquals(2, recorder.contexts.flatMap { it.units }.toSet().size)
            }
        }

    @Test
    fun `an update that finds stale units in one repository still confines them to it, as a rebuild does`() =
        TwoRepositoryWorkspace().use { workspace ->
            val recorder = ContextRecorder()
            workspace.engine(coreRegistry(recorder)).use { engine ->
                engine.update()
                recorder.contexts.clear()
                val record = "package org.example.library\n\nclass Record {\n    fun body() = 1\n}\n"
                workspace.write("library/model/src/main/kotlin/org/example/library/Record.kt", record)
                engine.update()
                val context = recorder.contexts.single()
                assertEquals(1, context.units.size)
                assertEquals(context.units, context.model)
                assertTrue(context.artifacts.all { it in context.units })
                engine.assertMatchesRebuild()
            }
        }

    @Test
    fun `a workspace of one repository hands the whole model to one source set job`() {
        val recorder = ContextRecorder()
        scanningSample(coreRegistry(recorder)) { _, engine ->
            engine.update()
            assertEquals(engine.graph().structure().modules.size, recorder.contexts.single().modules)
        }
    }
}
