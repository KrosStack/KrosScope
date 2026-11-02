package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFileReader
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTable
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqlTableReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqlTableReaderTest {
    private fun tables(text: String): List<SqlTable> {
        val file = SqFileReader.read(text)
        return file.tables.mapNotNull { SqlTableReader.read(it, file.imports) }
    }

    private fun SqlTable.described() = columns.map { "${it.name}:${it.type.written}" }

    @Test
    fun `columns get the Kotlin types of the SQLite dialect and their nullability`() {
        val table = tables(Resources.text("sqldelight/Task.sq")).single()
        assertEquals("TaskRow", table.name)
        assertEquals(
            listOf(
                "id:String", "title:String", "notes:String", "priority:Long", "due_at:Long?", "done:Long",
                "sync_state:String", "synced_at:Long?", "updated_at:Long",
            ),
            table.described(),
        )
    }

    @Test
    fun `an AS clause names the Kotlin type, resolved through the imports of the file`() {
        val note = tables(Resources.text("sqldelight/Note.sq")).first { it.name == "note" }
        assertEquals(
            listOf(
                "id:Long",
                "title:String",
                "kind:com.example.NoteKind",
                "pinned:kotlin.Boolean",
                "body:String?",
                "owner_id:Long?",
            ),
            note.described(),
        )
    }

    @Test
    fun `table options and primary key constraints are understood`() {
        val tables = tables("CREATE TABLE a (x INTEGER, y TEXT, PRIMARY KEY (x, y)) WITHOUT ROWID;")
        assertEquals(listOf("x:Long", "y:String"), tables.single().described())
    }

    @Test
    fun `the name of the table is located and quoted names are unquoted`() {
        val text = "CREATE TABLE IF NOT EXISTS \"my table\" (\"a b\" TEXT NOT NULL);"
        val table = tables(text).single()
        assertEquals("my table", table.name)
        assertEquals("\"my table\"", text.substring(table.nameSpan.start, table.nameSpan.end))
        assertEquals("a b", table.columns.single().name)
    }

    @Test
    fun `unknown SQL types are guesses and a table as select has no columns to read`() {
        val table = tables("CREATE TABLE a (x GEOMETRY, y);").single()
        assertEquals(listOf(true, true), table.columns.map { it.type.isGuess })
        assertEquals(listOf("x:Any?", "y:Any?"), table.described())
        assertNull(tables("CREATE TABLE b AS SELECT 1;").firstOrNull())
    }

    @Test
    fun `a default value that reads like a constraint or an AS clause changes nothing`() {
        val text = "CREATE TABLE t (a TEXT DEFAULT 'AS Long', b TEXT DEFAULT 'NOT NULL', " +
            "c TEXT DEFAULT \"PRIMARY KEY\");"
        assertEquals(listOf("a:String?", "b:String?", "c:String?"), tables(text).single().described())
    }
}
