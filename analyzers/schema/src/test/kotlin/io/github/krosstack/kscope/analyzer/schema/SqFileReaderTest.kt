package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqFileReader
import io.github.krosstack.kscope.analyzer.schema.sqldelight.sql.SqKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SqFileReaderTest {
    private val task = Resources.text("sqldelight/Task.sq")

    @Test
    fun `a file splits into unlabelled statements and labelled queries`() {
        val file = SqFileReader.read(task)
        val described = file.statements.map { "${it.label.orEmpty()}:${it.kind}" }
        assertEquals(
            listOf(
                ":CREATE_TABLE", ":OTHER", "selectAll:SELECT", "selectById:SELECT", "selectPending:SELECT",
                "countOpenByPriority:SELECT", "upsert:INSERT", "markSynced:UPDATE", "deleteById:DELETE",
                "lastSyncedAt:SELECT",
            ),
            described,
        )
        assertEquals(
            listOf(
                "selectAll",
                "selectById",
                "selectPending",
                "countOpenByPriority",
                "upsert",
                "markSynced",
                "deleteById",
                "lastSyncedAt",
            ),
            file.queries.map { it.label },
        )
        assertEquals(1, file.tables.size)
    }

    @Test
    fun `a label and the statement after it are told apart and located`() {
        val query = SqFileReader.read(task).queries.first { it.label == "selectById" }
        assertEquals("SELECT * FROM TaskRow WHERE id = ?", query.text)
        val label = requireNotNull(query.labelSpan)
        val index = TextIndex(task)
        assertEquals(18, index.range(label).startLine)
        assertEquals(1, index.range(label).startColumn)
        assertEquals("selectById", task.substring(label.start, label.end))
        assertEquals(19, index.range(query.declaration).let { it.endLine })
        assertEquals(
            "SELECT * FROM TaskRow WHERE id = ?",
            task.substring(query.textStart, query.textStart + query.text.length),
        )
    }

    @Test
    fun `import lines are kept apart from statements`() {
        val file = SqFileReader.read(Resources.text("sqldelight/Note.sq"))
        assertEquals(listOf("com.example.NoteKind", "kotlin.Boolean"), file.imports.map { it.name })
        assertEquals(11, file.statements.size)
    }

    @Test
    fun `semicolons in comments and strings do not end a statement`() {
        val text = "-- one; two\nCREATE TABLE a (x TEXT DEFAULT 'a;b'); /* ; */ q: SELECT ';' FROM a;"
        val file = SqFileReader.read(text)
        assertEquals(listOf(SqKind.CREATE_TABLE, SqKind.SELECT), file.statements.map { it.kind })
        assertEquals("q", file.statements.last().label)
        assertEquals("SELECT ';' FROM a", file.statements.last().text)
    }

    @Test
    fun `a trigger ends at the semicolon after its END`() {
        val text = "CREATE TRIGGER t AFTER INSERT ON a BEGIN UPDATE a SET x = 1; DELETE FROM b; END; q: SELECT 1;"
        val file = SqFileReader.read(text)
        assertEquals(2, file.statements.size)
        assertEquals(SqKind.OTHER, file.statements.first().kind)
        assertEquals("q", file.statements.last().label)
    }

    @Test
    fun `a statement without a closing semicolon is kept`() {
        val file = SqFileReader.read("q: SELECT 1")
        assertEquals("SELECT 1", file.queries.single().text)
        assertNull(SqFileReader.read("  \n").statements.firstOrNull())
    }
}
