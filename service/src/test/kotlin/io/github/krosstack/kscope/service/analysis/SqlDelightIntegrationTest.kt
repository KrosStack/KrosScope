package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.allRelationships
import io.github.krosstack.kscope.service.assertCompilerExact
import io.github.krosstack.kscope.service.danglingTargets
import io.github.krosstack.kscope.service.declared
import io.github.krosstack.kscope.service.edgesInto
import io.github.krosstack.kscope.service.generatesEdges
import io.github.krosstack.kscope.service.symbolOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scans a Kotlin Multiplatform module with a `.sq` file and a repository that calls its queries. The database, the row
 * class and the queries class exist only as stubs that the SQLDelight synthesizer makes from the build model and the
 * `.sq` file.
 */
class SqlDelightIntegrationTest {
    private val sample = SampleWorkspace("sqld")
    private val repository = "db/src/commonMain/kotlin/sample/sqld/db/NoteRepository.kt"
    private val statements = "db/src/commonMain/sqldelight/sample/sqld/db/Note.sq"
    private val pkg = "sample.sqld.db"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    @Test
    fun `calls of queries, reads of the queries property and of row properties bind with compiler evidence`() {
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertEquals(0, scan.diagnostics.warnings, "${scan.diagnostics.entries}")
            val graph = service.graph()
            listOf("selectAll", "selectById", "titles", "insertNote", "deleteAll").forEach {
                assertCompilerExact(graph.edgesInto(repository, "$pkg.NoteQueries.$it"), it)
            }
            assertCompilerExact(graph.edgesInto(repository, "$pkg.NotesDatabase.noteQueries"), "the queries property")
            assertCompilerExact(graph.edgesInto(repository, "$pkg.Note.title"), "a row property")
            assertCompilerExact(graph.edgesInto(repository, "$pkg.Note.body"), "a nullable row property")
        }
    }

    @Test
    fun `a query generates its functions and a table its row class, with convention evidence`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val query = graph.declared("$pkg.NoteQueries.selectAll", "sqldelight.query")
            assertEquals(statements, query.declaration?.artifact?.path?.value)
            val functions = graph.generatesEdges(query)
            assertEquals(2, functions.size, "The function with a mapper and the one without")
            assertTrue(functions.all { it.evidenceClass == EvidenceClass.CONVENTION })
            val table = graph.declared("$pkg.note", "sqldelight.table")
            val rowClass = graph.symbolOf(graph.generatesEdges(table).single())
            assertEquals("$pkg.Note", rowClass?.qualifiedName)
            assertEquals(NodeRef.OfSymbol(table.id), (rowClass?.origin as SymbolOrigin.Generated).sourceOfTruth)
            assertEquals("sqldelight", rowClass.origin.let { (it as SymbolOrigin.Generated).generator })
        }
    }

    @Test
    fun `a query refers to the table it reads, with textual evidence`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val table = graph.declared("$pkg.note", "sqldelight.table")
            val reads = graph.incoming(NodeRef.OfSymbol(table.id), setOf(RelationKind.REFERENCES))
            assertEquals(5, reads.size, "selectAll, selectById, titles, insertNote and deleteAll")
            assertTrue(reads.all { it.evidenceClass == EvidenceClass.TEXTUAL })
            assertTrue(reads.all { (it.resolution as Resolution.Resolved).certainty == Certainty.EXACT })
            assertEquals(setOf("read", "write"), reads.map { it.subKind }.toSet())
        }
    }

    @Test
    fun `every resolved target in the workspace names a symbol, the generated ones included`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            assertEquals(emptyList(), graph.danglingTargets())
            assertTrue(graph.allRelationships().any { graph.symbolOf(it)?.origin is SymbolOrigin.Generated })
        }
    }
}
