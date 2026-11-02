package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.proto.ProtoReadings
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private val first = RepositoryId("first")
private val second = RepositoryId("second")
private const val PROTO_PATH = "lib/src/main/proto/types.proto"

private fun buildRootOf(repository: RepositoryId) = BuildRootId("${repository.value}//")

private val model = BuildModel(
    buildRoots = listOf(first, second).map { Samples.buildRoot.copy(id = buildRootOf(it), repository = it) },
    modules = listOf(first, second).map {
        Samples.module.copy(
            id = ModuleId("${it.value}//:"),
            buildRoot = buildRootOf(it),
            path = ":",
            directory = RepoPath(""),
        )
    },
    targets = emptyList(),
    variants = emptyList(),
    sourceSets = emptyList(),
    dependencies = emptyList(),
)

private fun artifactIn(repository: RepositoryId, path: String, type: ArtifactType) =
    Samples.artifact.copy(ref = ArtifactRef(repository, RepoPath(path)), type = type, sourceSet = null)

class MultiRepositoryTest {
    @Test
    fun `an artifact is placed in a module of its own repository`() {
        val artifact = artifactIn(second, "schemas/p.Db/1.json", SchemaArtifactTypes.roomSchema)
        assertEquals(ModuleId("second//:"), Placement.of(artifact, model)?.module?.id)
    }

    @Test
    fun `proto files of different repositories with one path are read apart`() {
        val texts = mapOf(first to "package p;\nmessage Alpha {}\n", second to "package p;\nmessage Beta {}\n")
        val workspaceFiles = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray = texts.getValue(ref.repository).toByteArray()

            override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
        }
        val alpha = artifactIn(first, PROTO_PATH, SchemaArtifactTypes.proto)
        val beta = artifactIn(second, PROTO_PATH, SchemaArtifactTypes.proto)
        val readings = ProtoReadings(
            ProduceRequest(emptyList(), model, workspaceFiles, emptyList(), emptyMap(), listOf(alpha, beta)),
        )
        assertEquals(listOf("Alpha"), readings.of(alpha)?.types?.map { it.name })
        assertEquals(listOf("Beta"), readings.of(beta)?.types?.map { it.name })
        val indexed = readings.indexed().flatMap { proto -> proto.file.types.map { it.name } }
        assertEquals(listOf("Alpha", "Beta"), indexed)
    }
}
