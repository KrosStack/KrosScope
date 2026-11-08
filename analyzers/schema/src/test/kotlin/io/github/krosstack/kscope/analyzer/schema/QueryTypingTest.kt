package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryAnalyzer
import io.github.krosstack.kscope.analyzer.schema.sqldelight.query.QueryShape
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFileReader
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlSchema
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTableReader
import kotlin.test.Test
import kotlin.test.assertEquals

class QueryTypingTest {
    private fun shapeOf(schema: String, query: String): QueryShape {
        val file = SqFileReader.read("$schema\nq:\n$query;")
        val tables = file.tables.mapNotNull { SqlTableReader.read(it, file.imports) }
        return QueryAnalyzer(SqlSchema(tables)).shapeOf(file.queries.single())
    }

    @Test
    fun `a table named like an SQLite operator keeps its name in the query`() {
        val shape = shapeOf("CREATE TABLE \"match\" (id INTEGER NOT NULL);", "SELECT * FROM \"match\"")
        assertEquals("row match", shape.resultText())
    }

    @Test
    fun `one column is returned as its type unless it can be null, whatever its alias`() {
        val schema = "CREATE TABLE t (id INTEGER NOT NULL, body TEXT);"
        assertEquals("projection body: String?", shapeOf(schema, "SELECT body FROM t").resultText())
        assertEquals("single Long", shapeOf(schema, "SELECT id AS value FROM t").resultText())
    }

    @Test
    fun `SQLite row identifiers and change counts are single non-null Long values`() {
        listOf("last_insert_rowid", "changes", "total_changes").forEach { function ->
            assertEquals("single Long", shapeOf("", "SELECT $function()").resultText(), function)
        }
    }

    @Test
    fun `the columns of the optional side of an outer join can be null`() {
        val schema = "CREATE TABLE a (id INTEGER NOT NULL, tag TEXT NOT NULL);" +
            "\nCREATE TABLE b (id INTEGER NOT NULL, name TEXT NOT NULL);"
        fun join(kind: String, select: String) = shapeOf(schema, "SELECT $select FROM a $kind JOIN b ON b.id = a.id")
        assertEquals("projection id: Long, name: String?", join("LEFT", "a.id, b.name").resultText())
        assertEquals("projection id: Long?, name: String", join("RIGHT", "a.id, b.name").resultText())
        assertEquals("projection id: Long?, name: String?", join("FULL", "a.id, b.name").resultText())
        assertEquals("projection id: Long, name: String", join("INNER", "a.id, b.name").resultText())
        assertEquals(
            "projection id: Long, tag: String, id_: Long?, name: String?",
            join("LEFT", "*").resultText(),
        )
        assertEquals("projection id: Long?, name: String?", join("LEFT", "b.*").resultText())
    }
}
