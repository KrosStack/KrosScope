package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis.SqlDelightSynthesizer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.SyntheticSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightSynthesizerTest {
    private val files = sqlDelightFiles + mapOf(
        "data/db/src/commonMain/sqldelight/sample/notes/Note.sq" to Resources.text("sqldelight/Note.sq"),
    )
    private val synthesizer = SqlDelightSynthesizer()

    private fun run(spec: ModuleSpec = sqlDelightModule) = TestWorkspace(files, listOf(spec)).synthesize(synthesizer)

    private fun symbols(): List<Symbol> = run().first.facts().symbols()

    private fun SyntheticSource.described() = "// ${virtualPath}\n$text"

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
        val overloads = symbols().filter { it.qualifiedName == "sample.tasktide.data.db.TagQueries.selectForTask" }
        assertEquals(
            listOf("public fun selectForTask(task_id: String): app.cash.sqldelight.Query<String>"),
            overloads.map { it.signature },
        )
        assertEquals(2, symbols().count { it.qualifiedName == "sample.tasktide.data.db.TaskQueries.selectAll" })
    }

    @Test
    fun `generated symbols name their source of truth and a heuristic stub says so`() {
        val symbols = symbols()
        val column = symbols.single { it.qualifiedName == "sample.tasktide.data.db.TaskRow.due_at" }
        val origin = column.origin as SymbolOrigin.Generated
        assertEquals("sqldelight", origin.generator)
        assertEquals(EvidenceClass.CONVENTION, column.evidenceClass)
        val broken = symbols.filter { it.qualifiedName == "sample.notes.NoteQueries.broken" }
        assertEquals(listOf(Certainty.HEURISTIC), broken.map { it.certainty })
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
