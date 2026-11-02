package io.github.krosstack.kscope.engine.incremental

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TestWorkspace
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.kotlinSource
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.spi.ApiDigester
import io.github.krosstack.kscope.spi.ApiDigests
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.WorkspaceFiles

internal const val LIB_SOURCE = "lib/src/main/kotlin/Lib.kt"
internal const val APP_SOURCE = "app/src/main/kotlin/App.kt"

internal class MarkerApiDigester : ApiDigester {
    override var version = "1"
    override val consumes = setOf(kotlinSource)
    val calls = mutableListOf<List<String>>()
    var beforeDigest: () -> Unit = {}
    var examines = true

    /** The first line of a source is its digest; `no digest` has none and `unexamined` is not answered for. */
    override fun digest(sources: List<Artifact>, files: WorkspaceFiles): ApiDigests {
        beforeDigest()
        calls += sources.map { it.ref.path.value }
        if (!examines) return ApiDigests.NONE
        val markers = sources.associate { it.ref to String(files.read(it.ref), Charsets.UTF_8).lineSequence().first() }
        return ApiDigests(
            markers.filterValues { it != NO_DIGEST && it != UNEXAMINED },
            markers.filterValues { it == NO_DIGEST }.keys,
        )
    }

    private companion object {
        const val NO_DIGEST = "no digest"
        const val UNEXAMINED = "unexamined"
    }
}

internal class ApiRecordingProducer(
    override val apiDigester: MarkerApiDigester = MarkerApiDigester(),
    val recording: RecordingProducer = RecordingProducer("test.api", granularity = OwnershipKind.SOURCE_SET),
) : FactProducer by recording

internal class ApiDependencyProvider : BuildModelProvider {
    private val delegate = StructuralBuildModelProvider()
    override val producer = ProducerRef("test.api-dependencies", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val result = delegate.provide(request) as? BuildModelResult.Provided ?: return BuildModelResult.NotApplicable
        val modules = result.model.modules.associateBy { it.path }
        val library = modules.getValue(":lib").id
        val edge = dependency(NodeRef.OfModule(modules.getValue(":app").id), NodeRef.OfModule(library))
        val main = result.model.sourceSets.singleOrNull { it.module == library && it.name == "main" }
        val sourceSets = result.model.sourceSets.map { sourceSet ->
            if (main != null && sourceSet.module == main.module && sourceSet.name == "test") {
                sourceSet.copy(dependsOn = listOf(main.id))
            } else {
                sourceSet
            }
        }
        val sourceEdges = sourceSets.flatMap { sourceSet ->
            sourceSet.dependsOn.map { dependency(NodeRef.OfSourceSet(sourceSet.id), NodeRef.OfSourceSet(it)) }
        }
        val model = result.model.copy(sourceSets = sourceSets, dependencies = listOf(edge) + sourceEdges)
        return result.copy(model = model)
    }

    private fun dependency(from: NodeRef, to: NodeRef) = Relationship(
        from,
        RelationKind.DEPENDS_ON,
        null,
        Resolution.Resolved(to, Certainty.EXACT),
        EvidenceClass.BUILD_MODEL,
        null,
        Scope(),
    )
}

internal fun apiWorkspace(): TestWorkspace = TestWorkspace().also {
    it.seedRepositoryId("api-workspace")
    it.write("settings.gradle.kts", "include(\":lib\", \":app\")\n")
    it.write("lib/build.gradle.kts", "")
    it.write("app/build.gradle.kts", "")
    it.write(LIB_SOURCE, "lib API\nbody one\n")
    it.write(APP_SOURCE, "app API\nbody one\n")
}

internal fun apiRegistry(producer: FactProducer) = coreRegistry(producer, providers = listOf(ApiDependencyProvider()))
