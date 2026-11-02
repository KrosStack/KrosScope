package io.github.krosstack.kscope.service.workspace

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
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
import kotlin.test.assertTrue

private const val WEB = "tasktide-web"
private const val ALL = Int.MAX_VALUE

/** The gold entry `bundle_consumer_to_project:001`: what copies the bundle, and the package that builds it. */
private class BundleRow(
    val module: String,
    val kind: String,
    val name: String,
    val place: String,
    val target: Triple<String, String, String>,
)

private val bundleRow = BundleRow(
    module = ":server",
    kind = "gradle.copy-source",
    name = "../tasktide-web/dist",
    place = "tasktide-app/server/build.gradle.kts:14",
    target = Triple("npm.package", "@tasktide/web", "$WEB/package.json:2"),
)

private fun GraphReader.assertBundleLink(row: BundleRow) {
    val copy = symbols(SymbolQuery(kindId = row.kind, limit = ALL)).single { placeOf(it) == row.place }
    val link = outgoing(NodeRef.OfSymbol(copy.id), setOf(RelationKind.DEPENDS_ON)).single()
    val target = symbolOf(link.target())

    assertEquals(row.module, moduleOf(copy))
    assertEquals(row.name, copy.name)
    assertEquals(row.target, Triple(target.kind.id, target.name, placeOf(target)))
    assertEquals("web-bundle", link.subKind)
    assertEquals(EvidenceClass.BUILD_MODEL, link.evidenceClass)
}

private fun GraphReader.webBundleLinks(): List<Relationship> =
    relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "web-bundle", limit = ALL))

private fun GraphReader.bundleLinks(): List<String> = webBundleLinks().map { link ->
    "${placeOf(symbolOf(link.from))} -> ${placeOf(symbolOf(link.target()))}"
}

private fun GraphReader.moduleOf(symbol: Symbol): String {
    val unit = (symbol.id.identity() as ParsedSymbolId.Valid).identity.unit
    return structure().modules.single { it.id.value == unit }.path
}

private fun GraphReader.sourceMapLinks(): List<Relationship> =
    relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), subKind = "source-map"))

private fun Relationship.target(): NodeRef = (resolution as Resolution.Resolved).target

private fun GraphReader.symbolOf(node: NodeRef): Symbol = checkNotNull(symbol((node as NodeRef.OfSymbol).id))

private fun GraphReader.fileOf(symbol: Symbol): String {
    val location = checkNotNull(symbol.nameLocation)
    val directory = structure().repositories.single { it.id == location.artifact.repository }.directory.value
    return "$directory/${location.artifact.path.value}"
}

private fun GraphReader.placeOf(symbol: Symbol): String =
    "${fileOf(symbol)}:${checkNotNull(symbol.nameLocation).range.startLine}"

class WebBundleLinksTest {
    @BeforeTest
    fun requireGit() = assumeTrue(TestGit.available())

    @Test
    fun `the file of the built bundle is linked to the sources that its source map names`() =
        WorkspaceOfRepositories(withOutputs = true).use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val links = graph.sourceMapLinks()

                assertEquals(2, links.size)
                assertEquals(
                    listOf("$WEB/src/main.ts", "$WEB/src/tasks.ts"),
                    links.map { graph.fileOf(graph.symbolOf(it.target())) }.sorted(),
                )
                links.forEach {
                    val from = graph.symbolOf(it.from)
                    assertEquals("bundle.file", from.kind.id)
                    assertEquals("$WEB/dist/assets/index.js", graph.fileOf(from))
                    assertEquals("typescript.file", graph.symbolOf(it.target()).kind.id)
                    assertEquals(EvidenceClass.CONTENT, it.evidenceClass)
                }
            }
        }

    @Test
    fun `the package of the web repository records where its bundler writes the bundle`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val symbol = graph.symbols(SymbolQuery(kindId = "npm.package")).single()

                assertEquals("@tasktide/web", symbol.name)
                assertEquals("$WEB/package.json:2", graph.placeOf(symbol))
                assertEquals(
                    mapOf(
                        "npm.version" to "0.4.0",
                        "bundle.output" to "dist",
                        "bundle.output-declared" to "true",
                        "bundle.bundler" to "vite",
                    ),
                    symbol.attributes,
                )
            }
        }

    @Test
    fun `the bundle that a build writes after a scan is linked by the next scan, with no source changed`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                assertEquals(emptyList(), service.graph().symbols(SymbolQuery(kindId = "bundle.file")))
                assertEquals(emptyList(), service.graph().sourceMapLinks())

                workspace.writeOutputs()
                val rebuilt = service.scan()
                val graph = service.graph()
                val files = graph.artifacts().filter { it.generated != null }.map { it.ref.path.value }

                assertTrue(rebuilt.unitsProduced > 0)
                assertEquals(
                    listOf("dist/assets/index.js", "dist/assets/index.js.map", "dist/index.html"),
                    files.sorted(),
                )
                assertEquals(
                    listOf("$WEB/src/main.ts", "$WEB/src/tasks.ts"),
                    graph.sourceMapLinks().map { graph.fileOf(graph.symbolOf(it.target())) }.sorted(),
                )

                val unchanged = service.scan()
                assertEquals(0, unchanged.unitsProduced)
            }
        }

    @Test
    fun `the module that copies the bundle depends on the package that builds it, with no bundle on disk`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()

                service.graph().assertBundleLink(bundleRow)
                assertEquals(emptyList(), service.graph().symbols(SymbolQuery(kindId = "bundle.file")))
            }
        }

    @Test
    fun `the module that copies the bundle depends on the package that builds it, with the bundle on disk`() =
        WorkspaceOfRepositories(withOutputs = true).use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()

                service.graph().assertBundleLink(bundleRow)
                assertEquals(1, service.graph().symbols(SymbolQuery(kindId = "bundle.file")).size)
            }
        }

    @Test
    fun `the copy of the bundle is the same link before and after a build writes the bundle`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val before = service.graph().bundleLinks()

                workspace.writeOutputs()
                service.scan()
                val after = service.graph().bundleLinks()

                assertEquals(1, before.size)
                assertEquals(before, after)
            }
        }

    @Test
    fun `the copy source keeps the path as the script writes it and the place that the workspace holds`() =
        WorkspaceOfRepositories().use { workspace ->
            KrosScope.open(workspace.root).use { service ->
                service.scan()
                val graph = service.graph()
                val copy = graph.symbols(SymbolQuery(kindId = "gradle.copy-source", limit = ALL)).single()
                val web = graph.structure().repositories.single { it.directory.value == WEB }

                assertEquals("\$rootDir/../tasktide-web/dist", copy.signature)
                assertEquals("server/build.gradle.kts#processResources.from", copy.qualifiedName)
                assertEquals(
                    mapOf(
                        "copy.task" to "processResources",
                        "copy.into" to "static",
                        "copy.repository" to web.id.value,
                        "copy.path" to "dist",
                    ),
                    copy.attributes,
                )
                assertEquals(null, copy.scope.sourceSet)
            }
        }

    @Test
    fun `no copy source of the fixture is unresolved or noted`() = WorkspaceOfRepositories().use { workspace ->
        KrosScope.open(workspace.root).use { service ->
            val scan = service.scan()
            val links = service.graph().webBundleLinks()

            assertEquals(1, links.size)
            assertTrue(links.all { it.resolution is Resolution.Resolved }, "$links")
            assertEquals(emptyList(), scan.diagnostics.entries.filter { it.code.startsWith("web.") })
        }
    }
}
