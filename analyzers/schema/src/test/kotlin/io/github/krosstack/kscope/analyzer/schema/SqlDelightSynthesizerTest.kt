package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis.SqlDelightSynthesizer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val files = sqlDelightFiles + mapOf(
    "data/db/src/commonMain/sqldelight/sample/notes/Note.sq" to Resources.text("sqldelight/Note.sq"),
)
private val synthesizer = SqlDelightSynthesizer()

private fun run(spec: ModuleSpec = sqlDelightModule) = TestWorkspace(files, listOf(spec)).synthesize(synthesizer)

private fun symbols(): List<Symbol> = run().first.facts().symbols()

private fun SyntheticSource.described() = "// ${virtualPath}\n$text"

private fun sharedPackage(
    reverse: Boolean = false,
    queries: Map<String, String> = emptyMap(),
): Pair<List<FactBatch>, List<SyntheticSource>> {
    val directory = "data/media/src/commonMain/sqldelight/sample/media"
    val files = listOf("Album", "Track").associate { name ->
        val text = Resources.text("sqldelight/shared/$name.sq")
        val query = queries[name]
        "$directory/$name.sq" to if (query == null) text else text.substringBefore("summary:") + query
    }
    val spec = ModuleSpec(
        ":data:media",
        sourceSets = listOf("commonMain"),
        attributes = mapOf("sqldelight.database.MediaDatabase.package" to "sample.media"),
    )
    val workspace = TestWorkspace(files, listOf(spec))
    val module = workspace.model.modules.single()
    val artifacts = workspace.artifacts.let { if (reverse) it.reversed() else it }
    val unit = AnalysisUnit(OwnershipUnit(OwnershipKind.MODULE, module.id.value), artifacts, null, module)
    val batches = mutableListOf<FactBatch>()
    val stubs = synthesizer.synthesize(workspace.request(listOf(unit))) { batches += it }
    return batches to stubs
}

private fun assertDistinctSymbolIds(symbols: List<Symbol>) {
    val ids = symbols.map { it.id }
    assertEquals(ids.size, ids.distinct().size, "Every synthesized symbol id must be distinct")
}

private fun assertExactRowIdentifierQueries() {
    val symbols = sharedPackage().first.facts().symbols()
    listOf("AlbumQueries", "TrackQueries").forEach { queries ->
        val functions = symbols.filter { it.qualifiedName == "sample.media.$queries.lastInsertRowId" }
        assertEquals(
            listOf("public fun lastInsertRowId(): app.cash.sqldelight.Query<Long>"),
            functions.map { it.signature },
            queries,
        )
        assertEquals(listOf(Certainty.EXACT), functions.map { it.certainty })
    }
    assertTrue(symbols.none { it.qualifiedName == "sample.media.LastInsertRowId" })
}

private fun assertScalarSummaryDoesNotOwnProjection() {
    val (batches, stubs) = sharedPackage(queries = mapOf("Album" to "summary:\nSELECT count(*) AS total FROM album;"))
    val symbols = batches.facts().symbols()
    val scalar = symbols.single { it.qualifiedName == "sample.media.AlbumQueries.summary" }
    assertEquals("public fun summary(): app.cash.sqldelight.Query<Long>", scalar.signature)
    assertEquals(Certainty.EXACT, scalar.certainty)
    assertEquals(1, symbols.count { it.qualifiedName == "sample.media.Summary" })
    assertTrue(stubs.single { "public data class Summary(" in it.text }.virtualPath.endsWith("Track.sq.kt"))
    assertSummaryCertainty(symbols, "TrackQueries.summary", Certainty.EXACT)
    assertDistinctSymbolIds(symbols)
}

private fun assertSummaryCertainty(symbols: List<Symbol>, query: String, certainty: Certainty) {
    val functions = symbols.filter { it.qualifiedName == "sample.media.$query" }
    val signaturePrefix = "public fun ${query.substringAfterLast('.')}():"
    assertEquals(2, functions.size, query)
    assertEquals(listOf(certainty, certainty), functions.map { it.certainty }, query)
    assertEquals(
        "$signaturePrefix app.cash.sqldelight.Query<sample.media.Summary>",
        functions.single { it.signature?.startsWith(signaturePrefix) == true }.signature,
    )
}

private fun assertSummaryProperties(symbols: List<Symbol>) {
    assertEquals(1, symbols.count { it.qualifiedName == "sample.media.Summary" })
    assertEquals(
        listOf("public val total: Long", "public val newest: Long?"),
        symbols.filter {
            it.qualifiedName?.startsWith("sample.media.Summary.") == true &&
                it.signature?.startsWith("public val") == true
        }.map { it.signature },
    )
}

class SqlDelightSynthesizerTest {
    @Test
    fun `differing projections in a shared package downgrade every non owner overload`() {
        val projections = listOf(
            "count(*) AS total, max(length_seconds) AS newest, min(length_seconds) AS oldest",
            "max(length_seconds) AS newest, count(*) AS total",
            "count(*) AS total, max(title) AS newest",
            "count(*) AS total, max(length_seconds) AS longest",
        )
        projections.forEach { projection ->
            val (batches, stubs) = sharedPackage(
                reverse = true,
                queries = mapOf("Track" to "summary:\nSELECT $projection FROM track;"),
            )
            val symbols = batches.facts().symbols()
            assertSummaryProperties(symbols)
            assertTrue(stubs.single { "public data class Summary(" in it.text }.virtualPath.endsWith("Album.sq.kt"))
            assertDistinctSymbolIds(symbols)
            assertSummaryCertainty(symbols, "AlbumQueries.summary", Certainty.EXACT)
            assertSummaryCertainty(symbols, "TrackQueries.summary", Certainty.HEURISTIC)
        }
    }

    @Test
    fun `the first query owns a row class shared by differently cased labels`() {
        val queries = """
            summary:
            SELECT count(*) AS total, max(released_at) AS newest FROM album;

            Summary:
            SELECT count(*) AS total, max(released_at) AS newest, min(released_at) AS oldest FROM album;
        """.trimIndent()
        val (batches, stubs) = sharedPackage(queries = mapOf("Album" to queries, "Track" to ""))
        val symbols = batches.facts().symbols()
        assertSummaryProperties(symbols)
        assertEquals(1, stubs.count { "public data class Summary(" in it.text })
        assertDistinctSymbolIds(symbols)
        assertSummaryCertainty(symbols, "AlbumQueries.summary", Certainty.EXACT)
        assertSummaryCertainty(symbols, "AlbumQueries.Summary", Certainty.HEURISTIC)
    }

    @Test
    fun `the first artifact path owns a projection shared by both query classes`() {
        val (batches, stubs) = sharedPackage()
        assertDistinctSymbolIds(batches.facts().symbols())
        assertEquals(1, batches.facts().symbols().count { it.qualifiedName == "sample.media.Summary" })
        val owners = stubs.filter { "public data class Summary(" in it.text }
        assertEquals(
            listOf("sqldelight/data_media_src_commonMain_sqldelight_sample_media_Album.sq.kt"),
            owners.map { it.virtualPath },
        )
        listOf("AlbumQueries", "TrackQueries").forEach { queries ->
            val function = batches.facts().symbols().single {
                it.qualifiedName == "sample.media.$queries.summary" &&
                    it.signature?.startsWith("public fun summary():") == true
            }
            assertEquals("public fun summary(): app.cash.sqldelight.Query<sample.media.Summary>", function.signature)
            assertSummaryCertainty(batches.facts().symbols(), "$queries.summary", Certainty.EXACT)
        }
    }

    @Test
    fun `reversing source artifacts preserves stub texts and symbols`() {
        val (forwardBatches, forwardStubs) = sharedPackage()
        val (reverseBatches, reverseStubs) = sharedPackage(reverse = true)
        assertEquals(
            forwardStubs.sortedBy { it.virtualPath }.map { it.described() },
            reverseStubs.sortedBy { it.virtualPath }.map { it.described() },
        )
        assertEquals(
            forwardBatches.facts().symbols().sortedBy { it.id.value },
            reverseBatches.facts().symbols().sortedBy { it.id.value },
        )
    }

    @Test
    fun `each file with tables or queries gets a stub and each database an interface, in the source set`() {
        val (_, sources) = run()
        assertEquals(
            listOf(
                "sqldelight/TaskDatabase.kt",
                "sqldelight/data_db_src_commonMain_sqldelight_sample_notes_Note.sq.kt",
                "sqldelight/data_db_src_commonMain_sqldelight_sample_tasktide_data_db_Tag.sq.kt",
                "sqldelight/data_db_src_commonMain_sqldelight_sample_tasktide_data_db_Task.sq.kt",
            ),
            sources.map { it.virtualPath }.sorted(),
        )
        assertTrue(sources.all { it.sourceSet == TestRepository.sourceSetId(":data:db", "commonMain") })
        assertTrue(sources.all { it.generator == "sqldelight" && it.language == "kotlin" })
    }

    @Test
    fun `the stubs follow the naming rules of the SQLDelight generator`() {
        val text = run().second.sortedBy { it.virtualPath }.joinToString("\n") { it.described() }
        Golden.assertMatches("sqldelight-stubs.kt.txt", text)
    }

    @Test
    fun `a module without a database in the build model gets the file stubs and a note`() {
        val (batches, sources) = run(ModuleSpec(":data:db", sourceSets = listOf("commonMain")))
        assertTrue(sources.none { it.virtualPath == "sqldelight/TaskDatabase.kt" })
        assertTrue(sources.isNotEmpty())
        val note = batches.facts().filterIsInstance<io.github.krosstack.kscope.model.Fact.OfDiagnostic>().single().value
        assertEquals("sqldelight.database-unknown", note.code.value)
    }

    @Test
    fun `the ids are the ones the analyzer derives from the stub syntax`() {
        val ids = symbols().map { Render.id(it.id).removePrefix(":data:db@commonMain:") }
        listOf(
            "sample/tasktide/data/db/TaskRow#",
            "sample/tasktide/data/db/TaskRow#due_at.",
            "sample/tasktide/data/db/TaskRow#`<init>`(String,String,String,Long,Long,Long,String,Long,Long).",
            "sample/tasktide/data/db/TaskQueries#selectAll().",
            "sample/tasktide/data/db/TaskQueries#selectAll(Function).",
            "sample/tasktide/data/db/TaskQueries#selectById(String).",
            "sample/tasktide/data/db/TaskQueries#selectById(String,Function).",
            "sample/tasktide/data/db/TaskQueries#upsert(TaskRow).",
            "sample/tasktide/data/db/TaskQueries#markSynced(Long,String).",
            "sample/tasktide/data/db/CountOpenByPriority#open_count.",
            "sample/tasktide/data/db/LastSyncedAt#last_synced.",
            "sample/tasktide/data/db/TagQueries#selectForTask(String).",
            "sample/tasktide/data/db/TaskDatabase#taskQueries.",
            "sample/tasktide/data/db/TaskDatabase#Companion#invoke(SqlDriver).",
            "sample/notes/Note#kind.",
            "sample/notes/NoteQueries#byIds(Collection).",
            "sample/notes/NoteQueries#search(String,Boolean,Long,Long).",
        ).forEach { expected -> assertTrue(expected in ids, "$expected in $ids") }
    }

    @Test
    fun `a query with one column returns its type and has no mapper overload`() {
        assertExactRowIdentifierQueries()
        assertScalarSummaryDoesNotOwnProjection()
        val overloads = symbols().filter { it.qualifiedName == "sample.tasktide.data.db.TagQueries.selectForTask" }
        assertEquals(
            listOf("public fun selectForTask(task_id: String): app.cash.sqldelight.Query<String>"),
            overloads.map { it.signature },
        )
        assertEquals(2, symbols().count { it.qualifiedName == "sample.tasktide.data.db.TaskQueries.selectAll" })
    }

    @Test
    fun `generated symbols name their source of truth and a heuristic stub says so`() {
        val facts = run().first.facts()
        val symbols = facts.symbols()
        val column = symbols.single { it.qualifiedName == "sample.tasktide.data.db.TaskRow.due_at" }
        val origin = column.origin as SymbolOrigin.Generated
        assertEquals("sqldelight", origin.generator)
        assertEquals(EvidenceClass.CONVENTION, column.evidenceClass)
        val broken = symbols.filter { it.qualifiedName == "sample.notes.NoteQueries.broken" }
        assertEquals(listOf(Certainty.HEURISTIC), broken.map { it.certainty })
        val exact = symbols.filter { it.qualifiedName == "sample.tasktide.data.db.TaskQueries.selectAll" }
        assertEquals(listOf(Certainty.EXACT, Certainty.EXACT), exact.map { it.certainty })
        val generated = facts.relationships().filter { it.kind == RelationKind.GENERATES }
        (broken + exact).forEach { symbol ->
            val edge = generated.single {
                (it.resolution as Resolution.Resolved).target == NodeRef.OfSymbol(symbol.id)
            }
            assertEquals(symbol.certainty, (edge.resolution as Resolution.Resolved).certainty, symbol.qualifiedName)
        }
        assertTrue(
            symbols.filter { it.qualifiedName == "sample.notes.NoteQueries.search" }.all {
                it.certainty ==
                    Certainty.EXACT
            },
        )
    }

    @Test
    fun `GENERATES joins tables, columns and queries to their classes, properties and functions`() {
        val generated = run().first.facts().relationships().filter { it.kind == RelationKind.GENERATES }
        val described = generated.map { Render.relationship(it).replace(":data:db@commonMain:", "") }
        val source = "sqldelight/tables/sample/tasktide/data/db/TaskRow"
        val generatedClass = "sample/tasktide/data/db/TaskRow"
        assertTrue("GENERATES/ $source# -> $generatedClass# CONVENTION" in described)
        assertTrue("GENERATES/ $source#id. -> $generatedClass#id. CONVENTION" in described)
        assertEquals(
            2,
            described.count {
                it.startsWith(
                    "GENERATES/ sqldelight/queries/sample/tasktide/data/db/Task#selectAll. ->",
                )
            },
            "Both overloads are generated from the query",
        )
    }
}
