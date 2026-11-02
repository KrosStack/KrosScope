package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.TestGit
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ALL = Int.MAX_VALUE
private const val SPEC = "tasktide-spec/openapi/tasktide.yaml"
private const val PROTO = "tasktide-spec/proto/sample/tasktide/sync/v1/sync.proto"
private const val APP = "tasktide-app"
private const val CLIENT = "$APP/client"
private const val SERVER = "$APP/server"
private const val CLIENT_CODE = "$CLIENT/src/main/kotlin/sample/tasktide/client"
private const val SERVER_CODE = "$SERVER/src/main/kotlin/sample/tasktide/server"

/** An input of a generator in a build script, and the spec file it reads: the gold entries of `spec_input_to_spec`. */
private class InputRow(
    val module: String,
    val kind: String,
    val place: String,
    val targetKind: String,
    val targetFile: String,
)

/** A use of generated code and the spec symbol it comes from: the gold entries of `generated_surface_to_spec`. */
private class SurfaceRow(val file: String, val lines: List<Int>, val kind: String, val name: String, val place: String)

private val inputRows = listOf(
    InputRow(":client", "openapi-generator.input", "$CLIENT/build.gradle.kts:14", "openapi.document", SPEC),
    InputRow(":client", "wire.source-path", "$CLIENT/build.gradle.kts:20", "proto.file", PROTO),
    InputRow(":server", "wire.source-path", "$SERVER/build.gradle.kts:8", "proto.file", PROTO),
)

private val surfaceRows = listOf(
    SurfaceRow("$CLIENT_CODE/TaskClient.kt", listOf(6), "openapi.operation", "listTasks", "$SPEC:8"),
    SurfaceRow("$CLIENT_CODE/SyncClient.kt", listOf(5, 6), "proto.message", "PushRequest", "$PROTO:5"),
    SurfaceRow("$SERVER_CODE/SyncHandler.kt", listOf(6), "proto.message", "PushRequest", "$PROTO:5"),
    SurfaceRow("$SERVER_CODE/SyncHandler.kt", listOf(6), "proto.message", "PushReply", "$PROTO:10"),
)

private fun GraphReader.symbolsOf(kind: String): List<Symbol> = symbols(SymbolQuery(kindId = kind, limit = ALL))

private fun GraphReader.pathOf(artifact: ArtifactRef): String =
    "${structure().repositories.single { it.id == artifact.repository }.directory.value}/${artifact.path.value}"

private fun GraphReader.placeOf(symbol: Symbol): String {
    val location = checkNotNull(symbol.nameLocation)
    return "${pathOf(location.artifact)}:${location.range.startLine}"
}

private fun GraphReader.moduleOf(symbol: Symbol): String {
    val unit = (symbol.id.identity() as ParsedSymbolId.Valid).identity.unit
    return structure().modules.single { it.id.value == unit }.path
}

private fun GraphReader.targetOf(link: Relationship): Symbol? =
    ((link.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.let { symbol(it.id) }

private fun GraphReader.dependenciesOf(input: Symbol): List<Relationship> =
    outgoing(NodeRef.OfSymbol(input.id), setOf(RelationKind.DEPENDS_ON))

class GeneratorInputLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `each generator input of a build script is linked to the spec file that its generator reads`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()

                inputRows.forEach { row ->
                    val input = graph.symbolsOf(row.kind).single { graph.placeOf(it) == row.place }
                    val link = graph.dependenciesOf(input).single()
                    val target = checkNotNull(graph.targetOf(link))

                    assertEquals(row.module, graph.moduleOf(input), row.place)
                    assertEquals(row.targetKind, target.kind.id, row.place)
                    assertEquals(row.targetFile, graph.pathOf(checkNotNull(target.nameLocation).artifact), row.place)
                    assertEquals("generator-input", link.subKind)
                    assertEquals(EvidenceClass.BUILD_MODEL, link.evidenceClass)
                }
            }
        }

    @Test
    fun `an input symbol is named after the last segment of its path and keeps the path as the script writes it`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val spec = graph.symbolsOf("openapi-generator.input").single()
                val wire = graph.symbolsOf("wire.source-path").sortedBy { graph.moduleOf(it) }

                assertEquals("tasktide.yaml", spec.name)
                assertEquals("\$rootDir/../tasktide-spec/openapi/tasktide.yaml", spec.signature)
                assertEquals(mapOf("openapi-generator.name" to "kotlin"), spec.attributes)
                assertEquals(listOf("proto", "proto"), wire.map { it.name })
                assertEquals(listOf("\$rootDir/../tasktide-spec/proto"), wire.map { it.signature }.distinct())
                assertEquals(listOf(null, null, null), (wire + spec).map { it.scope.sourceSet })
            }
        }

    @Test
    fun `a use of generated code is linked to a generated symbol that comes from the spec`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()

                surfaceRows.forEach { row -> graph.assertGeneratedFrom(row) }
            }
        }

    @Test
    fun `no generator input of the fixture is unresolved or noted`() = WorkspaceOfRepositories().use { workspace ->
        KrosScope.open(workspace.root).use { service ->
            val scan = service.scan()
            val query = RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), limit = ALL)
            val links = service.graph().relationships(query).filter { it.subKind == "generator-input" }
            val noted = scan.diagnostics.entries.filter { note ->
                listOf("generator.", "wire.", "openapi-generator.").any { note.code.startsWith(it) }
            }

            assertEquals(3, links.size)
            assertTrue(links.all { it.resolution is Resolution.Resolved }, "$links")
            assertEquals(emptyList(), noted)
        }
    }

    @Test
    fun `the generated classes of the spec repository are declared in the modules that read it`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val generated = graph.symbols(SymbolQuery(name = "PushRequest", limit = ALL))
                    .filter { it.origin is SymbolOrigin.Generated && it.kind.id == "kotlin.class" }
                val tasksApi = graph.symbols(SymbolQuery(name = "TasksApi", limit = ALL))
                    .filter { it.origin is SymbolOrigin.Generated }

                assertEquals(listOf(":client", ":server"), generated.map { graph.moduleOfSourceSet(it) }.sorted())
                assertEquals(listOf(":client"), tasksApi.map { graph.moduleOfSourceSet(it) })
                assertEquals("openapi-generator", (tasksApi.single().origin as SymbolOrigin.Generated).generator)
            }
        }

    private fun GraphReader.assertGeneratedFrom(row: SurfaceRow) {
        val reached = generatedSymbolsUsedAt(row)
        val sources = reached.flatMap { generated -> generatorsOf(generated) }
        val description = "${row.file} ${row.name}"

        assertTrue(
            sources.any { (source, _) -> source.kind.id == row.kind && source.name == row.name },
            "$description is not used: $reached",
        )
        sources.filter { (source, _) -> source.name == row.name }.forEach { (source, edge) ->
            assertEquals(row.place, placeOf(source), description)
            assertEquals(EvidenceClass.CONVENTION, edge.evidenceClass, description)
        }
    }

    private fun GraphReader.moduleOfSourceSet(symbol: Symbol): String {
        val sourceSet = structure().sourceSets.single { it.id == symbol.scope.sourceSet }
        return structure().modules.single { it.id == sourceSet.module }.path
    }

    private fun GraphReader.generatedSymbolsUsedAt(row: SurfaceRow): List<Symbol> =
        relationships(RelationshipQuery(limit = ALL)).filter { link ->
            val site = link.site
            site != null && pathOf(site.artifact) == row.file && site.range.startLine in row.lines
        }.mapNotNull { targetOf(it) }.filter { it.origin is SymbolOrigin.Generated }.distinctBy { it.id }

    private fun GraphReader.generatorsOf(generated: Symbol): List<Pair<Symbol, Relationship>> =
        incoming(NodeRef.OfSymbol(generated.id), setOf(RelationKind.GENERATES)).mapNotNull { edge ->
            ((edge.from as? NodeRef.OfSymbol)?.let { symbol(it.id) })?.let { it to edge }
        }
}
