package io.github.krosstack.kscope.analyzer.schema.sqldelight

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val PATH = "data/db/src/main/sqldelight/com/sample/Sample.sq"

private fun facts(): List<Fact> = TestWorkspace(
    mapOf(PATH to "some_select:\nSELECT 1;\n\nsome_select:\nSELECT 1;\n"),
    listOf(ModuleSpec(":data:db", attributes = mapOf("sqldelight.database.Db.package" to "com.sample"))),
).produce(SqlDelightProducer()).facts()

class SqlDelightDuplicateLabelTest {
    @Test
    fun `a query label written twice in one file declares one query`() {
        val symbols = facts().symbols()

        assertEquals(1, symbols.count { it.qualifiedName == "com.sample.SampleQueries.some_select" })
        assertEquals(symbols.size, symbols.map { it.id }.distinct().size)
    }

    @Test
    fun `the dropped repeat of a query label is reported with the file and the label`() {
        val note = facts().filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals("sqldelight.query-label-repeated", note.code.value)
        assertTrue(PATH in note.message && "some_select" in note.message)
    }
}
