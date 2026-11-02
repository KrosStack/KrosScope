package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmLibraryLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `without a build the JAR file of the client is linked to the module of the kit that builds it`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val link = graph.jvmLibraryLinks().single()
                val resolution = link.resolution as Resolution.Resolved

                assertEquals(NodeRef.OfModule(graph.module("tasktide-app", ":client")), link.from)
                assertEquals(NodeRef.OfModule(graph.module("tasktide-kit", ":text")), resolution.target)
                assertEquals(Certainty.HEURISTIC, resolution.certainty)
                assertEquals(EvidenceClass.BUILD_MODEL, link.evidenceClass)
                assertEquals("tasktide-text-1.0.0.jar", link.attributes["file"])
                assertNull(link.attributes["content"])
                assertEquals(graph.repository("tasktide-app"), link.site?.artifact?.repository)
                assertEquals("client/build.gradle.kts", link.site?.artifact?.path?.value)
            }
        }

    @Test
    fun `with the JAR on disk the link rests on its content`() =
        WorkspaceOfRepositories(withOutputs = true).use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val link = graph.jvmLibraryLinks().single()
                val resolution = link.resolution as Resolution.Resolved

                assertEquals(NodeRef.OfModule(graph.module("tasktide-kit", ":text")), resolution.target)
                assertEquals(Certainty.EXACT, resolution.certainty)
                assertEquals(EvidenceClass.CONTENT, link.evidenceClass)
                assertEquals("matched", link.attributes["content"])
                assertEquals("tasktide-text-1.0.0.jar", link.attributes["file"])
            }
        }

    @Test
    fun `the call of the client into the JAR resolves to the declaration in the kit`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val targets = graph.relationships(RelationshipQuery(evidenceClass = EvidenceClass.COMPILER))
                    .filter { it.site?.artifact?.path?.value == TASK_SLUG }
                    .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id }
                    .mapNotNull { graph.symbol(it)?.qualifiedName }
                assertTrue("sample.tasktide.text.Slugs.of" in targets, "$targets")
            }
        }

    private fun GraphReader.jvmLibraryLinks(): List<Relationship> =
        relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "jvm-library"))

    private fun GraphReader.repository(directory: String) =
        structure().repositories.single { it.directory.value == directory }.id

    private fun GraphReader.module(directory: String, path: String) = structure().let { structure ->
        val roots = structure.buildRoots.filter { it.repository == repository(directory) }.map { it.id }.toSet()
        structure.modules.single { it.path == path && it.buildRoot in roots }.id
    }

    private companion object {
        const val TASK_SLUG = "client/src/main/kotlin/sample/tasktide/client/TaskSlug.kt"
    }
}
