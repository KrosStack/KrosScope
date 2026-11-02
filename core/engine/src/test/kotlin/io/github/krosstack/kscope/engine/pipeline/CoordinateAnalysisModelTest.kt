package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.RecordingProducer
import io.github.krosstack.kscope.engine.TwoRepositoryWorkspace
import io.github.krosstack.kscope.engine.build.StructuralBuildModelProvider
import io.github.krosstack.kscope.engine.coreRegistry
import io.github.krosstack.kscope.engine.structure.TestGit
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Whatever version a consumer pins, the link to the module that publishes the coordinates is part of the analysis. */
class CoordinateAnalysisModelTest {
    private val producer = RecordingProducer("by-source-set", granularity = OwnershipKind.SOURCE_SET)

    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    private fun analysedLinks(built: String, pinned: String): List<Relationship> =
        TwoRepositoryWorkspace().use { workspace ->
            val providers = listOf(CoordinateProvider(built, pinned))
            workspace.engine(coreRegistry(producer, providers = providers)).use { it.update() }
            checkNotNull(producer.model).dependencies.filter { it.subKind == "maven-coordinates" }
        }

    private fun Relationship.certainty() = (resolution as Resolution.Resolved).certainty

    @Test
    fun `a dependency on the version built from source is in the analysed model as an exact edge`() {
        val edge = analysedLinks(built = "1.2.0", pinned = "1.2.0").single()

        assertEquals(RelationKind.DEPENDS_ON, edge.kind)
        assertEquals("true", edge.attributes["versionMatch"])
        assertEquals(Certainty.EXACT, edge.certainty())
    }

    @Test
    fun `a dependency on another version is in the analysed model as a heuristic edge`() {
        val edge = analysedLinks(built = "1.2.0", pinned = "0.9.0").single()

        assertEquals("false", edge.attributes["versionMatch"])
        assertEquals("0.9.0", edge.attributes["version"])
        assertEquals(Certainty.HEURISTIC, edge.certainty())
    }

    @Test
    fun `a dependency on a library whose version is not read is in the analysed model as a heuristic edge`() {
        val edge = analysedLinks(built = "unspecified", pinned = "3.9.0").single()

        assertEquals("unknown", edge.attributes["versionMatch"])
        assertEquals(Certainty.HEURISTIC, edge.certainty())
    }
}

/** The structural model, with the library publishing `org.example:model` and the application pinning it. */
private class CoordinateProvider(private val built: String, private val pinned: String) : BuildModelProvider {
    private val structural = StructuralBuildModelProvider()
    override val producer = ProducerRef("test.coordinates", "1")
    override val requiresTrust = false

    override fun provide(request: BuildModelRequest): BuildModelResult {
        val provided = structural.provide(request)
        if (provided !is BuildModelResult.Provided) return provided
        return provided.copy(model = adapted(provided.model, request))
    }

    private fun adapted(model: BuildModel, request: BuildModelRequest) = when (request.repository.directory.value) {
        "library" -> model.publishing()
        "application" -> model.depending(ArtifactRef(request.repository.id, RepoPath(SCRIPT)))
        else -> model
    }

    private fun BuildModel.publishing() = copy(
        modules = modules.map {
            it.copy(
                evidenceClass = EvidenceClass.BUILD_MODEL,
                certainty = Certainty.EXACT,
                attributes = mapOf("maven.publications" to "org.example:model:$built"),
            )
        },
    )

    private fun BuildModel.depending(script: ArtifactRef) = copy(
        modules = modules.map { it.copy(evidenceClass = EvidenceClass.BUILD_MODEL, certainty = Certainty.EXACT) },
        dependencies = modules.map { module ->
            Relationship(
                from = NodeRef.OfModule(module.id),
                kind = RelationKind.DEPENDS_ON,
                subKind = null,
                resolution = Resolution.Resolved(NodeRef.OfExternal("maven:org.example:model"), Certainty.EXACT),
                evidenceClass = EvidenceClass.BUILD_MODEL,
                site = SourceLocation(script, TextRange(3, 1, 3, 9)),
                scope = Scope(),
                attributes = mapOf("configuration" to "implementation", "version" to pinned),
            )
        },
    )

    private companion object {
        const val SCRIPT = "app/build.gradle.kts"
    }
}
