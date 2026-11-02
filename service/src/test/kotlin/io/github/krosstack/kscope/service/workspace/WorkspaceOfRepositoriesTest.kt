package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceOfRepositoriesTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `the fixture scans as six repositories`() = WorkspaceOfRepositories().use { workspace ->
        KrosScope.open(workspace.root).use { service ->
            service.scan()
            val directories = service.graph().structure().repositories.map { it.directory.value }.sorted()
            assertEquals(workspace.repositories, directories)
            assertEquals(6, directories.size)
        }
    }

    @Test
    fun `the bundle of a package is inventoried as output and the other build products are not`() =
        WorkspaceOfRepositories(withOutputs = true).use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val artifacts = service.graph().artifacts()
                val paths = artifacts.map { it.ref.path.value }
                val outputs = artifacts.filter { it.generated != null }
                assertTrue(paths.none { it.endsWith(".jar") || "/build/" in it }, "$paths")
                assertTrue("src/main.ts" in paths, "$paths")
                assertEquals(
                    listOf("dist/assets/index.js", "dist/assets/index.js.map", "dist/index.html"),
                    outputs.map { it.ref.path.value }.sorted(),
                )
                assertEquals(setOf("output:vite"), outputs.mapNotNull { it.generated?.generator }.toSet())
            }
        }

    @Test
    fun `the coordinate dependency of the client reaches the format module of the kit`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val query = RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "maven-coordinates")
                val edges = graph.relationships(query)
                val expected = NodeRef.OfModule(graph.module("tasktide-app", ":client")) to
                    NodeRef.OfModule(graph.module("tasktide-kit", ":format"))
                val pairs = edges.map { it.from to (it.resolution as? Resolution.Resolved)?.target }
                assertTrue(expected in pairs, "$edges")
            }
        }

    @Test
    fun `the client's call into the kit resolves across the repositories`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val targets = graph.relationships(RelationshipQuery(evidenceClass = EvidenceClass.COMPILER))
                    .filter { it.site?.artifact?.path?.value == DUE_LABEL }
                    .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id }
                    .mapNotNull { graph.symbol(it)?.qualifiedName }
                assertTrue("sample.tasktide.format.DueDates.label" in targets, "$targets")
            }
        }

    private fun GraphReader.module(directory: String, path: String) = structure().let { structure ->
        val repository = structure.repositories.single { it.directory.value == directory }.id
        val roots = structure.buildRoots.filter { it.repository == repository }.map { it.id }.toSet()
        structure.modules.single { it.path == path && it.buildRoot in roots }.id
    }

    private companion object {
        const val DUE_LABEL = "client/src/main/kotlin/sample/tasktide/client/DueLabel.kt"
    }
}
