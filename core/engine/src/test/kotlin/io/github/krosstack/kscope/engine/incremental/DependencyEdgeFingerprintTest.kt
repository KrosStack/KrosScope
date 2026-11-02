package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.ScanEngine
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.assertMatchesRebuild
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.descriptorOf
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val B_SCRIPT = "b/build.gradle.kts"

/**
 * `:a` depends on `:b` and `:b` on `:c`. The lines of the build script of `:b` set the configuration of the second
 * edge, the source set it is scoped to, the library that `:a` compiles against and the certainty of the first edge.
 */
private class ScriptedDependencyProvider : BuildModelProvider {
    override val producer = ProducerRef("test.scripted-dependencies", "1")
    override val requiresTrust = true

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val structural = StructuralBuildModelProvider().provide(request) as? BuildModelResult.Provided
            ?: return BuildModelResult.NotApplicable
        val model = structural.model
        val script = request.artifacts.single { it.ref.path.value == B_SCRIPT }
        val lines = String(request.files.read(script.ref)).lines()
        val modules = model.modules.associate { it.path to it.id }
        val edges = listOf(
            edge(modules.getValue(":a"), modules.getValue(":b"), "implementation", null, certaintyOf(lines[3])),
            edge(modules.getValue(":b"), modules.getValue(":c"), lines[0], scopeOf(model, modules, lines[1])),
        )
        val classpath = lines[2].takeIf { it.isNotEmpty() }?.let { listOf("/libs/$it.jar") }.orEmpty()
        val libraryUser = model.sourceSets.single { it.name == "main" && it.module == modules.getValue(":a") }
        return structural.copy(
            model = model.copy(dependencies = edges),
            classpaths = mapOf(libraryUser.id to classpath).filterValues { it.isNotEmpty() },
        )
    }

    private fun scopeOf(model: BuildModel, modules: Map<String, ModuleId>, name: String) =
        model.sourceSets.singleOrNull { it.module == modules.getValue(":b") && it.name == name }?.id

    private fun certaintyOf(line: String?) = if (line == "heuristic") Certainty.HEURISTIC else Certainty.EXACT

    private fun edge(
        from: ModuleId,
        to: ModuleId,
        configuration: String,
        scope: SourceSetId?,
        certainty: Certainty = Certainty.EXACT,
    ) = Relationship(
        from = NodeRef.OfModule(from),
        kind = RelationKind.DEPENDS_ON,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfModule(to), certainty),
        evidenceClass = EvidenceClass.BUILD_MODEL,
        site = null,
        scope = Scope(sourceSet = scope),
        attributes = mapOf("configuration" to configuration),
    )
}

/** Records which modules a source set can see, following direct dependencies and the `api` edges beyond them. */
private class VisibleModulesProducer : FactProducer {
    override val descriptor = descriptorOf("test.visible-modules", kotlinSource, OwnershipKind.SOURCE_SET)
    val producedKeys = mutableListOf<String>()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            producedKeys += unit.owner.key
            val visible = request.model.visibleFrom(checkNotNull(unit.module).id)
            val message = "${unit.owner.key} sees $visible"
            val fact = Fact.OfDiagnostic(Diagnostic(DiagnosticSeverity.INFO, ReasonCode("test.visible"), message, null))
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint("ignored"), listOf(fact)))
        }
    }

    private fun BuildModel.visibleFrom(module: ModuleId): List<String> {
        val seen = linkedSetOf<ModuleId>()
        val pending = ArrayDeque(edgesFrom(module).map { it.target() })
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (seen.add(next)) pending += edgesFrom(next).filter { it.exposes() }.map { it.target() }
        }
        return seen.map { it.value.substringAfterLast(':') }.sorted()
    }

    private fun BuildModel.edgesFrom(module: ModuleId) =
        dependencies.filter { (it.from as NodeRef.OfModule).id == module }

    private fun Relationship.target() = ((resolution as Resolution.Resolved).target as NodeRef.OfModule).id

    private fun Relationship.exposes() =
        attributes["configuration"] == "api" && scope.sourceSet?.value?.endsWith("@test") != true
}

class DependencyEdgeFingerprintTest {
    private val producer = VisibleModulesProducer()

    private fun <T> scanning(block: (TestWorkspace, ScanEngine) -> T): T = TestWorkspace().use { workspace ->
        workspace.write("settings.gradle.kts", "include(\":a\", \":b\", \":c\")\n")
        listOf("a", "b", "c").forEach { workspace.write("$it/build.gradle.kts", "") }
        workspace.write("a/src/main/kotlin/A.kt", "class A\n")
        workspace.write("b/src/main/kotlin/B.kt", "class B\n")
        workspace.write("b/src/test/kotlin/BTest.kt", "class BTest\n")
        workspace.write("c/src/main/kotlin/C.kt", "class C\n")
        val registry = coreRegistry(producer, providers = listOf(ScriptedDependencyProvider()))
        workspace.engine(registry, trusted = true).use { block(workspace, it) }
    }

    private fun TestWorkspace.declare(
        configuration: String,
        scope: String = "",
        library: String = "",
        certainty: String = "",
    ) = write(B_SCRIPT, "$configuration\n$scope\n$library\n$certainty\n")

    private fun produced(name: String) = producer.producedKeys.any { it.endsWith(":$name") }

    @Test
    fun `a module is produced again when a dependency it reaches through another module stops being exposed`() =
        scanning { workspace, engine ->
            workspace.declare("api")
            engine.update()
            producer.producedKeys.clear()

            workspace.declare("implementation")
            engine.update()

            assertTrue(produced("a@main"), "${producer.producedKeys}")
            assertFalse(produced("c@main"), "${producer.producedKeys}")
            engine.assertMatchesRebuild()
        }

    @Test
    fun `a module is produced again when the dependency in between moves to a test source set`() =
        scanning { workspace, engine ->
            workspace.declare("api")
            engine.update()
            producer.producedKeys.clear()

            workspace.declare("api", scope = "test")
            engine.update()

            assertTrue(produced("a@main"), "${producer.producedKeys}")
            engine.assertMatchesRebuild()
        }

    @Test
    fun `a module is produced again when a dependency it has becomes heuristic`() = scanning { workspace, engine ->
        workspace.declare("api")
        engine.update()
        producer.producedKeys.clear()

        workspace.declare("api", certainty = "heuristic")
        engine.update()

        assertTrue(produced("a@main"), "${producer.producedKeys}")
        engine.assertMatchesRebuild()
    }

    @Test
    fun `a module is produced again when the library on its classpath changes`() = scanning { workspace, engine ->
        workspace.declare("api", library = "lib-1.0")
        engine.update()
        producer.producedKeys.clear()

        workspace.declare("api", library = "lib-1.1")
        engine.update()

        assertTrue(produced("a@main"), "${producer.producedKeys}")
        engine.assertMatchesRebuild()
    }

    @Test
    fun `an unchanged workspace reuses every unit`() = scanning { workspace, engine ->
        workspace.declare("api", library = "lib-1.0")
        engine.update()
        producer.producedKeys.clear()

        val report = engine.update()

        assertEquals(0, report.unitsProduced)
        assertTrue(producer.producedKeys.isEmpty(), "${producer.producedKeys}")
    }

    @Test
    fun `editing a build script without changing a dependency reuses every unit`() = scanning { workspace, engine ->
        workspace.declare("api")
        engine.update()
        producer.producedKeys.clear()

        workspace.write(B_SCRIPT, "api\n\n\n// a comment\n")
        engine.update()

        assertTrue(producer.producedKeys.isEmpty(), "${producer.producedKeys}")
    }
}
