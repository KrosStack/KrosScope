package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.ParameterType
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryAnalyzer
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryResult
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryShape
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFileReader
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlSchema
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTableReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun QueryShape.parameterList(): String = parameters.joinToString(", ") { parameter ->
    val type = when (val type = parameter.type) {
        is ParameterType.OfColumn -> type.type.written
        is ParameterType.Many -> "Collection<${type.element.base}>"
        is ParameterType.Row -> "row ${type.table.name}"
        ParameterType.Count -> "count"
        ParameterType.Unknown -> "?"
    }
    "${parameter.name}: $type"
}

internal fun QueryShape.resultText(): String = when (val result = result) {
    QueryResult.Mutation -> "mutation"

    QueryResult.Unknown -> "unknown"

    is QueryResult.RowOfTable -> "row ${result.table.name}"

    is QueryResult.Single -> "single ${result.type.written}"

    is QueryResult.Projection -> "projection " + result.columns.joinToString(", ") { column ->
        "${column.name}: ${column.type.written}"
    }
}

class QueryShapeTest {
    private val files = listOf("Task", "Tag", "Note").map { SqFileReader.read(Resources.text("sqldelight/$it.sq")) }
    private val analyzer = QueryAnalyzer(
        SqlSchema(files.flatMap { file -> file.tables.mapNotNull { SqlTableReader.read(it, file.imports) } }),
    )
    private val queries = listOf("Task", "Tag", "Note").zip(files).flatMap { (name, file) ->
        file.queries.map { "$name.${it.label}" to it }
    }.toMap()

    private fun shape(name: String): QueryShape = analyzer.shapeOf(checkNotNull(queries[name]))

    @Test
    fun `select star from one table returns the row of the table`() {
        assertEquals("row TaskRow", shape("Task.selectAll").resultText())
        assertEquals("id: String", shape("Task.selectById").parameterList())
        assertEquals("row TaskRow", shape("Task.selectById").resultText())
    }

    @Test
    fun `columns and computed values with an alias make a projection`() {
        assertEquals("projection priority: Long, open_count: Long", shape("Task.countOpenByPriority").resultText())
        assertEquals("projection last_synced: Long?", shape("Task.lastSyncedAt").resultText())
    }

    @Test
    fun `one column without an alias is returned as its type`() {
        assertEquals("single String", shape("Tag.selectForTask").resultText())
        assertEquals("single Long", shape("Note.countAll").resultText())
    }

    @Test
    fun `statements that change data return nothing and take the arguments of their columns`() {
        assertEquals("mutation", shape("Task.upsert").resultText())
        assertEquals("TaskRow: row TaskRow", shape("Task.upsert").parameterList())
        assertEquals("synced_at: Long?, id: String", shape("Task.markSynced").parameterList())
        assertEquals("task_id: String, name: String", shape("Tag.insert").parameterList())
        assertEquals("title: String, kind: com.example.NoteKind", shape("Note.insertNote").parameterList())
        assertEquals("title: String, id: Long", shape("Note.rename").parameterList())
        assertEquals("", shape("Note.purge").parameterList())
    }

    @Test
    fun `named arguments keep their names and limits are counts`() {
        assertEquals(
            "query: String, pinned: kotlin.Boolean, limit: count, offset: count",
            shape("Note.search").parameterList(),
        )
        assertEquals("ids: Collection<Long>", shape("Note.byIds").parameterList())
    }

    @Test
    fun `a join selects the named columns of both tables`() {
        assertEquals("projection title: String, owner_name: String", shape("Note.withOwner").resultText())
        assertEquals("id: Long", shape("Note.withOwner").parameterList())
    }

    @Test
    fun `arguments of one column get underscores until their names differ`() {
        assertEquals("id: Long, id_: Long, title: String, title_: String", shape("Note.between").parameterList())
    }

    @Test
    fun `a query the parser cannot read is a guess with the names found in the text`() {
        val broken = shape("Note.broken")
        assertEquals("unknown", broken.resultText())
        assertEquals("id: ?", broken.parameterList())
        assertTrue(broken.isGuess)
    }

    @Test
    fun `fully inferred queries are not guesses`() {
        assertFalse(shape("Task.selectAll").isGuess)
        assertFalse(shape("Task.markSynced").isGuess)
        assertFalse(shape("Note.withOwner").isGuess)
        assertFalse(shape("Tag.selectForTask").isGuess)
    }
}
