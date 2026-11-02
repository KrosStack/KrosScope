package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SPEC = "tasktide-spec/openapi/tasktide.yaml"
private const val PROTO = "tasktide-spec/proto/sample/tasktide/sync/v1/sync.proto"
private const val ALL = Int.MAX_VALUE
private const val ROUTES = "tasktide-app/server/src/main/kotlin/sample/tasktide/server/Routes.kt"

private fun GraphReader.symbolsOf(kind: String): List<Symbol> = symbols(SymbolQuery(kindId = kind, limit = ALL))

private fun GraphReader.symbolOf(node: NodeRef): Symbol = checkNotNull(symbol((node as NodeRef.OfSymbol).id))

private fun GraphReader.lineOf(symbol: Symbol): Int = checkNotNull(symbol.nameLocation).range.startLine

private fun GraphReader.fileNameOf(symbol: Symbol): String =
    checkNotNull(symbol.nameLocation).artifact.path.value.substringAfterLast('/')

private fun GraphReader.moduleOf(symbol: Symbol): String {
    val sourceSet = structure().sourceSets.single { it.id == symbol.scope.sourceSet }
    return structure().modules.single { it.id == sourceSet.module }.path
}

private fun GraphReader.placeOf(symbol: Symbol): String {
    val location = checkNotNull(symbol.nameLocation)
    val directory = structure().repositories.single { it.id == location.artifact.repository }.directory.value
    return "$directory/${location.artifact.path.value}:${location.range.startLine}"
}

class SpecLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `an operation of the spec repository is linked to the Ktor route of the app that implements it`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val link = graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.BINDS)))
                    .single { it.subKind == "openapi-route" }
                val operation = graph.symbolOf(link.from)
                val route = graph.symbolOf((link.resolution as Resolution.Resolved).target)

                assertEquals("openapi.operation", operation.kind.id)
                assertEquals("listTasks", operation.name)
                assertEquals("GET /tasks", operation.signature)
                assertEquals("$SPEC:8", graph.placeOf(operation))
                assertEquals("ktor.route", route.kind.id)
                assertEquals("GET /tasks", route.name)
                assertEquals("$ROUTES:9", graph.placeOf(route))
                assertEquals(":server", graph.moduleOf(route))
                assertEquals(EvidenceClass.CONVENTION, link.evidenceClass)
                assertEquals(Certainty.PROBABLE, (link.resolution as Resolution.Resolved).certainty)
            }
        }

    @Test
    fun `the only link through a spec in the workspace is that one, with nothing unresolved`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val links = service.graph().relationships(RelationshipQuery(kinds = setOf(RelationKind.BINDS)))
                    .filter { it.subKind == "openapi-route" }

                assertEquals(1, links.size)
                assertEquals(1, links.count { it.resolution is Resolution.Resolved })
            }
        }

    @Test
    fun `the spec files hold a document symbol and a proto file symbol in the module of their repository`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val document = graph.symbolsOf("openapi.document").single()
                val file = graph.symbolsOf("proto.file").single()

                assertEquals("TaskTide", document.name)
                assertEquals("$SPEC:1", graph.placeOf(document))
                assertEquals("tasktide.yaml", graph.fileNameOf(document))
                assertEquals(":", graph.moduleOf(document))
                assertEquals("sync.proto", file.name)
                assertEquals("package sample.tasktide.sync.v1", file.signature)
                assertEquals("$PROTO:3", graph.placeOf(file))
                assertEquals(":", graph.moduleOf(file))
            }
        }

    @Test
    fun `the schemas of the document and the operations are contained by the document symbol`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val document = graph.symbolsOf("openapi.document").single()
                val inside = graph.symbols(SymbolQuery(container = document.id, limit = ALL))

                assertEquals(listOf("openapi.operation", "openapi.schema"), inside.map { it.kind.id }.sorted())
                assertEquals(listOf("listTasks", "Task"), inside.sortedBy { graph.lineOf(it) }.map { it.name })
            }
        }

    @Test
    fun `the spec files of the spec repository are artifacts of the types that read them`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val types = service.graph().artifacts().associate { it.ref.path.value to it.type.id }

                assertEquals("openapi.document", types["openapi/tasktide.yaml"])
                assertEquals("proto.source", types["proto/sample/tasktide/sync/v1/sync.proto"])
            }
        }
}
