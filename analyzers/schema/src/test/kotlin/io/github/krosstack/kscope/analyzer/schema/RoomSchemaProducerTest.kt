package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.room.RoomSchemaProducer
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Symbol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val SCHEMA_PATH = "data/settings/schemas/sample.tasktide.data.settings.SettingsDatabase/1.json"

class RoomSchemaProducerTest {
    private val files = mapOf(
        SCHEMA_PATH to Resources.text("room/1.json"),
        "data/settings/src/main/res/raw/other.json" to "{\"formatVersion\": 1}",
        "data/settings/package.json" to "{\"name\": \"x\"}",
    )
    private val workspace = TestWorkspace(files, listOf(ModuleSpec(":data:settings")))

    private fun facts(): List<Fact> = workspace.produce(RoomSchemaProducer()).facts()

    private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

    @Test
    fun `a json file with a format version and the entities of a database is a Room schema`() {
        assertEquals("room.schema", workspace.artifact(SCHEMA_PATH).type.id)
        assertEquals("unknown", workspace.artifact("data/settings/package.json").type.id)
        assertEquals("unknown", workspace.artifact("data/settings/src/main/res/raw/other.json").type.id)
    }

    @Test
    fun `entities and fields become schema symbols named for the table and the column`() {
        val described = facts().symbols().map { "${it.kind.id} ${it.qualifiedName}" }
        assertEquals(
            listOf(
                "room.schema.entity sample.tasktide.data.settings.SettingsDatabase.user_preference",
                "room.schema.field sample.tasktide.data.settings.SettingsDatabase.user_preference.name",
                "room.schema.field sample.tasktide.data.settings.SettingsDatabase.user_preference.value",
                "room.schema.field sample.tasktide.data.settings.SettingsDatabase.user_preference.updated_at",
            ),
            described,
        )
    }

    @Test
    fun `the symbols are generated artifacts and carry the database and version`() {
        val symbols = facts().symbols()
        assertTrue(symbols.all { it.evidenceClass == EvidenceClass.GENERATED_ARTIFACT })
        val entity = symbols.first()
        assertEquals("sample.tasktide.data.settings.SettingsDatabase", entity.attributes["room.schema.database"])
        assertEquals("1", entity.attributes["room.schema.version"])
        assertEquals(
            "CREATE TABLE IF NOT EXISTS `user_preference` (`name` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`name`))",
            entity.signature,
        )
        val field = symbols.single { it.name == "updated_at" }
        assertEquals("updatedAt", field.attributes["room.schema.field-path"])
        assertEquals("updated_at INTEGER NOT NULL", field.signature)
    }

    @Test
    fun `locations are the lines of the table name and the column names`() {
        val byName = facts().symbols().associateBy { it.name }
        assertEquals(8, byName.getValue("user_preference").line())
        assertEquals(13, byName.getValue("name").line())
        assertEquals(25, byName.getValue("updated_at").line())
    }

    @Test
    fun `ids name the module, no source set, the database, the version and the column`() {
        val ids = facts().symbols().map { Render.id(it.id) }
        val database = "sample/tasktide/data/settings/SettingsDatabase"
        val expected = ":data:settings@.:room/schema/$database/v1#user_preference#updated_at."
        assertTrue(expected in ids, "$ids")
    }

    @Test
    fun `a file that looks like a schema but is not readable is a warning`() {
        val broken = TestWorkspace(
            mapOf("data/settings/schemas/x.Db/1.json" to "{\"formatVersion\": 1, \"database\": {\"entities\": 5}"),
            listOf(ModuleSpec(":data:settings")),
        )
        val facts = broken.produce(RoomSchemaProducer()).facts()
        assertEquals(emptyList(), facts.symbols())
        val diagnostic = facts.filterIsInstance<Fact.OfDiagnostic>().single().value
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals("room.schema-unreadable", diagnostic.code.value)
    }
}
