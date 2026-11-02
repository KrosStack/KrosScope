package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.BuildRoot
import io.github.krosstack.kscope.model.BuildRootId
import io.github.krosstack.kscope.model.BuildSystem
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.retrieval.analysis.AnalysisFacts
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class RetrievalGraphTest {
    @Test
    fun `module fallback attributes matching directories to the artifact repository`() {
        val graph = RetrievalGraph(reader())
        val files = graph.artifacts.keys
        val expected = listOf("a//:app", "b//:app").map(::ModuleId)

        assertEquals(expected, files.map(graph::module))
        assertEquals(expected, AnalysisFacts(graph).scope(files).modules)
        assertEquals(
            expected,
            AnalysisFacts(graph).modules(files.map { RankedFile(it, 1.0, FileRole.READ, emptyList()) }).map { it.id },
        )
        assertNull(graph.module(files.first().copy(repository = RepositoryId("unknown"))))
    }

    @Test
    fun `module queries seed only files in that repository`() {
        val reader = reader()
        val files = reader.artifacts()

        files.forEach { file ->
            val related = GraphQueries(reader).related("${file.ref.repository.value}//:app")

            assertEquals(listOf(file.ref), related.files.map { it.artifact })
        }
    }

    @Test
    fun `module fallback keeps the deepest directory and root module in the same repository`() {
        val graph = RetrievalGraph(reader())
        val file = graph.artifacts.keys.last()

        assertEquals(ModuleId("b//:"), graph.module(file.copy(path = RepoPath("settings.gradle.kts"))))
        assertEquals(ModuleId("b//:"), graph.module(file.copy(path = RepoPath("application/build.gradle.kts"))))
        assertEquals(
            ModuleId("b//:app:feature"),
            graph.module(file.copy(path = RepoPath("app/feature/build.gradle.kts"))),
        )
    }

    @Test
    fun `source set membership takes precedence over matching directories`() {
        val reader = reader()
        val file = reader.artifacts().last()
        val structure = reader.structure()
        val module = structure.modules.first { it.buildRoot == BuildRootId("b//") && it.directory.value.isEmpty() }
        val sourceSet = SourceSet(
            SourceSetId("b-main"), module.id, "main", SourceSetRole.MAIN,
            emptyList(), emptyList(), emptyList(), emptyList(), EvidenceClass.BUILD_MODEL, Certainty.EXACT,
        )
        val fixture = object : GraphReader by reader {
            override fun artifacts() = listOf(file.copy(sourceSet = sourceSet.id))
            override fun structure() = structure.copy(sourceSets = listOf(sourceSet))
        }

        assertEquals(module.id, RetrievalGraph(fixture).module(file.ref))
    }

    private fun reader(): GraphReader {
        val roots = listOf("a", "b").map { name ->
            BuildRoot(BuildRootId("$name//"), RepositoryId(name), RepoPath(""), BuildSystem.GRADLE)
        }
        val modules = roots.flatMap { root -> listOf("", "app", "app/feature").map { module(root, it) } }
        val files = roots.map { root ->
            artifact("app/build.gradle.kts").let { it.copy(ref = it.ref.copy(repository = root.repository)) }
        }
        val fixture = RetrievalFixture(files)
        return object : GraphReader by fixture {
            override fun structure() = WorkspaceStructure(
                roots.map { Repository(it.repository, it.repository.value, null, null, RepoPath(it.repository.value)) },
                roots,
                modules,
                emptyList(),
                emptyList(),
                emptyList(),
            )
        }
    }

    private fun module(root: BuildRoot, directory: String) = Module(
        ModuleId("${root.id.value}:${directory.replace('/', ':')}"),
        root.id,
        ":${directory.replace('/', ':')}",
        RepoPath(directory),
        emptyList(),
        emptySet(),
        null,
        EvidenceClass.BUILD_MODEL,
        Certainty.EXACT,
    )
}
