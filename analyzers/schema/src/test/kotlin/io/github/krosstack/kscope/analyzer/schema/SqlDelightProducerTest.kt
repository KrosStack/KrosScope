package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightLinker
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightProducer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal val sqlDelightFiles = mapOf(
    "data/db/src/commonMain/sqldelight/sample/tasktide/data/db/Task.sq" to Resources.text("sqldelight/Task.sq"),
    "data/db/src/commonMain/sqldelight/sample/tasktide/data/db/Tag.sq" to Resources.text("sqldelight/Tag.sq"),
    "data/db/src/commonMain/sqldelight/migrations/1.sqm" to
        "ALTER TABLE TaskRow ADD COLUMN notes TEXT NOT NULL DEFAULT '';",
)

internal val sqlDelightModule = ModuleSpec(
    ":data:db",
    sourceSets = listOf("commonMain"),
    attributes = mapOf("sqldelight.database.TaskDatabase.package" to "sample.tasktide.data.db"),
)

class SqlDelightProducerTest {
    private val taskPath = "data/db/src/commonMain/sqldelight/sample/tasktide/data/db/Task.sq"
    private val workspace = TestWorkspace(sqlDelightFiles, listOf(sqlDelightModule))

    private fun batches(): List<FactBatch> = workspace.produce(SqlDelightProducer())

    private fun factsOf(path: String): List<Fact> = batches().single { it.owner.key == path }.facts

    private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

    @Test
    fun `statement files below a sqldelight directory are recognised, migrations included`() {
        assertEquals(listOf("sqldelight.source"), workspace.artifacts.map { it.type.id }.distinct())
        assertEquals(3, workspace.artifacts.size)
    }

    @Test
    fun `tables, columns and queries become schema symbols`() {
        val described = factsOf(taskPath).symbols().map { "${it.kind.id} ${it.qualifiedName}" }
        assertEquals(
            listOf("sqldelight.table sample.tasktide.data.db.TaskRow") +
                listOf("id", "title", "notes", "priority", "due_at", "done", "sync_state", "synced_at", "updated_at")
                    .map { "sqldelight.column sample.tasktide.data.db.TaskRow.$it" } +
                listOf(
                    "selectAll",
                    "selectById",
                    "selectPending",
                    "countOpenByPriority",
                    "upsert",
                    "markSynced",
                    "deleteById",
                    "lastSyncedAt",
                )
                    .map { "sqldelight.query sample.tasktide.data.db.TaskQueries.$it" },
            described,
        )
    }

    @Test
    fun `the name location is the line of the table name, the column, or the label of the query`() {
        val byName = factsOf(taskPath).symbols().associateBy { it.qualifiedName }
        assertEquals(1, byName.getValue("sample.tasktide.data.db.TaskRow").line())
        assertEquals(2, byName.getValue("sample.tasktide.data.db.TaskRow.id").line())
        assertEquals(15, byName.getValue("sample.tasktide.data.db.TaskQueries.selectAll").line())
        assertEquals(36, byName.getValue("sample.tasktide.data.db.TaskQueries.lastSyncedAt").line())
        assertEquals(
            1 to 11,
            requireNotNull(
                byName.getValue("sample.tasktide.data.db.TaskRow").declaration,
            ).range.let { it.startLine to it.endLine },
        )
    }

    @Test
    fun `symbol ids carry the module, the source set and the package of the file`() {
        val ids = factsOf(taskPath).symbols().map { Render.id(it.id) }
        assertTrue(":data:db@commonMain:sqldelight/tables/sample/tasktide/data/db/TaskRow#due_at." in ids, "$ids")
        assertTrue(":data:db@commonMain:sqldelight/queries/sample/tasktide/data/db/Task#selectAll." in ids, "$ids")
    }

    @Test
    fun `a migration declares nothing`() {
        assertEquals(emptyList(), factsOf("data/db/src/commonMain/sqldelight/migrations/1.sqm"))
    }

    @Test
    fun `queries refer symbolically to the tables they read and write`() {
        val references = factsOf(taskPath).filterIsInstance<Fact.OfSymbolicReference>().map { it.value }
        val described = references.map {
            "${Render.node(
                it.from,
            ).substringAfterLast(':')} ${it.subKind} ${it.key} ${it.site?.range?.startLine}"
        }
        assertTrue(described.any { it.endsWith("Task#selectById. read TaskRow 19") }, "$described")
        assertTrue(described.any { it.endsWith("Task#markSynced. write TaskRow 31") }, "$described")
        assertTrue(described.any { it.endsWith("Task#upsert. write TaskRow 28") }, "$described")
        assertEquals(8, references.size)
        assertTrue(references.all { it.namespace == "sqldelight.table" && it.evidenceClass == EvidenceClass.TEXTUAL })
    }

    @Test
    fun `the linker answers a reference with the table of the module, also from another file`() {
        val extra = TestWorkspace(
            sqlDelightFiles + mapOf(
                "data/db/src/commonMain/sqldelight/sample/tasktide/data/db/Join.sq" to
                    "joined:\nSELECT * FROM TaskRow JOIN TagRow ON TagRow.task_id = TaskRow.id;\n" +
                    "missing:\nSELECT * FROM nowhere;",
            ),
            listOf(sqlDelightModule),
        )
        val facts = extra.produce(SqlDelightProducer()).facts()
        val graph = FakeGraph(facts, extra.model)
        val answers = mutableListOf<FactBatch>()
        SqlDelightLinker().link(graph) { answers += it }
        val relationships = answers.facts().relationships()
        assertEquals(graph.symbolicReferences().size, relationships.size)
        val joined = relationships.filter { Render.node(it.from).endsWith("Join#joined.") }
        assertEquals(
            listOf("TagRow", "TaskRow"),
            joined.map {
                Render.node(
                    (it.resolution as Resolution.Resolved).target,
                ).substringAfterLast('/').substringBefore('#')
            }.sorted(),
        )
        assertTrue(joined.all { (it.resolution as Resolution.Resolved).certainty == Certainty.EXACT })
        val missing = relationships.single { Render.node(it.from).endsWith("Join#missing.") }
        assertEquals("sqldelight.table-not-found", (missing.resolution as Resolution.Unresolved).reason.value)
        assertTrue(joined.all { it.from is NodeRef.OfSymbol })
    }
}
