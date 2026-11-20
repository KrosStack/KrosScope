package io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val QUERY_FILE = "CREATE TABLE item (id INTEGER NOT NULL);\n\nselectAll:\nSELECT * FROM item;\n"

private val twoDatabases = ModuleSpec(
    ":data:db",
    attributes = mapOf(
        "sqldelight.database.DatabaseA.package" to "com.example",
        "sqldelight.database.DatabaseA.srcDirs" to "src/main/projectA",
        "sqldelight.database.DatabaseB.package" to "com.example",
        "sqldelight.database.DatabaseB.srcDirs" to "src/main/projectB",
    ),
)

private fun scan(vararg paths: String) =
    TestWorkspace(paths.associateWith { QUERY_FILE }, listOf(twoDatabases)).synthesize(SqlDelightSynthesizer()).first

private fun List<Symbol>.names() = mapNotNull { it.qualifiedName }

class SqlDelightDatabaseAssignmentTest {
    @Test
    fun `files that no directory ties to a database are left out of every database and noted`() {
        val batches = scan(
            "data/db/src/projectA/sqldelight/com/sample/Test.sq",
            "data/db/src/projectB/sqldelight/com/sample/Test.sq",
        )
        val facts = batches.facts()
        val ids = facts.symbols().map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(facts.symbols().names().none { it.startsWith("com.example.Database") })
        val notes = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value }
        assertEquals(listOf("sqldelight.database-ambiguous"), notes.map { it.code.value }.distinct())
        assertEquals(2, notes.size)
    }

    @Test
    fun `same-named files under the configured directories of two databases each get their own query property`() {
        val symbols = scan(
            "data/db/src/main/projectA/sqldelight/com/a/Test.sq",
            "data/db/src/main/projectB/sqldelight/com/b/Test.sq",
        ).facts().symbols()
        val names = symbols.names()
        assertEquals(1, names.count { it == "com.example.DatabaseA.testQueries" })
        assertEquals(1, names.count { it == "com.example.DatabaseB.testQueries" })
        val ids = symbols.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `a database with unread directories keeps the name rule from guessing, but read directories still assign`() {
        val databases = ModuleSpec(
            ":data:db",
            attributes = mapOf(
                "sqldelight.database.DatabaseA.package" to "com.example",
                "sqldelight.database.DatabaseA.srcDirs" to "src/main/projectA",
                "sqldelight.database.DatabaseB.package" to "com.example",
                "sqldelight.database.DatabaseB.srcDirsUnread" to "true",
                "sqldelight.database.DatabaseC.package" to "com.example",
            ),
        )
        val files = listOf(
            "data/db/src/main/projectA/sqldelight/com/a/Test.sq",
            "data/db/src/main/sqldelight/databasec/Other.sq",
        )
        val facts = TestWorkspace(files.associateWith { QUERY_FILE }, listOf(databases))
            .synthesize(SqlDelightSynthesizer()).first.facts()

        val names = facts.symbols().names()
        assertEquals(1, names.count { it == "com.example.DatabaseA.testQueries" })
        assertTrue(names.none { it.startsWith("com.example.DatabaseC.") && it.endsWith("Queries") })
        val notes = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value.code.value }
        assertEquals(listOf("sqldelight.database-ambiguous"), notes)
    }

    @Test
    fun `classes that two files of one source set would both generate are made once and the repeat is reported`() {
        val databases = ModuleSpec(
            ":data:db",
            attributes = mapOf(
                "sqldelight.database.DatabaseA.package" to "com.example",
                "sqldelight.database.DatabaseA.srcDirsUnread" to "true",
                "sqldelight.database.DatabaseB.package" to "com.example",
                "sqldelight.database.DatabaseB.srcDirsUnread" to "true",
            ),
        )
        val files = listOf(
            "data/db/src/main/projectA/sqldelight/com/sample/Test.sq",
            "data/db/src/main/projectB/sqldelight/com/sample/Test.sq",
        )
        val facts = TestWorkspace(files.associateWith { QUERY_FILE }, listOf(databases))
            .synthesize(SqlDelightSynthesizer()).first.facts()

        val ids = facts.symbols().map { it.id }
        assertTrue(ids.isNotEmpty())
        assertEquals(ids.size, ids.distinct().size)
        val repeated = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value }
            .filter { it.code.value == "sqldelight.generated-name-repeated" }
        assertEquals(2, repeated.size)
        assertTrue(repeated.all { it.message.startsWith("data/db/src/main/projectB/sqldelight/com/sample/Test.sq") })
    }
}
