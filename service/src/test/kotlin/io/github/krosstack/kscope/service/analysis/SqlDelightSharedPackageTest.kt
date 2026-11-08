package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.assertCompilerExact
import io.github.krosstack.kscope.service.edgesInto
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlDelightSharedPackageTest {
    private val sample = SampleWorkspace("sqld-shared")
    private val repository = "db/src/commonMain/kotlin/sample/sqldshared/db/MediaRepository.kt"
    private val pkg = "sample.sqldshared.db"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `a guessed query signature has heuristic generation and an exact compiler bound call`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val name = "$pkg.AlbumQueries.titleCodepoints"
            val function = graph.symbols(SymbolQuery(qualifiedName = name)).single {
                it.origin is SymbolOrigin.Generated &&
                    it.signature?.startsWith("public fun titleCodepoints():") == true
            }
            assertEquals(Certainty.HEURISTIC, function.certainty)
            assertCompilerExact(graph.edgesInto(repository, name), name)
            val generates = graph.incoming(NodeRef.OfSymbol(function.id), setOf(RelationKind.GENERATES)).single()
            assertEquals(EvidenceClass.CONVENTION, generates.evidenceClass)
            assertEquals(Certainty.HEURISTIC, (generates.resolution as Resolution.Resolved).certainty)
        }
    }

    @Test
    fun `queries sharing a package scan cleanly and bind their functions and shared row properties`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            val graph = service.graph()
            listOf("AlbumQueries", "TrackQueries").forEach { queries ->
                listOf("lastInsertRowId", "summary").forEach { function ->
                    val name = "$pkg.$queries.$function"
                    assertCompilerExact(graph.edgesInto(repository, name), name)
                }
            }
            assertCompilerExact(graph.edgesInto(repository, "$pkg.Summary.total"), "the shared summary total")
        }
    }
}
