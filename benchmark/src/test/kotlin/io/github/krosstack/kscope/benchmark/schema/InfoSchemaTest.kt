package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.benchmark.TemporaryTree
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ServiceJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The JSON of `info`, with the `settings` field, conforms to the published schema; and the check can fail. */
class InfoSchemaTest {
    private val base: Path = Files.createTempDirectory("kscope-info-schema")
    private val workspace: Path = Files.createDirectories(base.resolve("workspace"))
    private val index: Path = base.resolve("index")
    private val schema: SchemaCheck = SchemaCheck(published())

    @AfterTest
    fun cleanUp() {
        TemporaryTree.delete(base)
    }

    private fun published(): JsonObject {
        val root = Path.of(requireNotNull(System.getProperty("kscope.gold"))).parent.parent
        return Json.parseToJsonElement(Files.readString(root.resolve("docs/schemas/info.schema.json"))).jsonObject
    }

    private fun write(relative: String, text: String) {
        val target = workspace.resolve(relative)
        Files.createDirectories(target.parent)
        Files.writeString(target, text)
    }

    private fun infoJson(): JsonObject = KrosScope.open(workspace, OpenOptions(indexDirectory = index)).use { scope ->
        Json.parseToJsonElement(ServiceJson.encode(scope.info())).jsonObject
    }

    private fun scan() {
        write("settings.gradle.kts", "include(\":lib\")\n")
        write("lib/build.gradle.kts", "")
        write("lib/src/main/kotlin/Lib.kt", "class Lib\n")
        KrosScope.open(workspace, OpenOptions(indexDirectory = index)).use { it.scan() }
    }

    @Test
    fun `info of a workspace without a settings file conforms`() {
        assertEquals(emptyList(), schema.violations(infoJson()))
    }

    @Test
    fun `info of a scanned workspace with a valid settings file conforms`() {
        scan()
        write("kscope.toml", "format = 1\n[resources]\nworkers = 2\nworker-heap-mib = 512\n")

        assertEquals(emptyList(), schema.violations(infoJson()))
    }

    @Test
    fun `info of a scanned workspace with scan settings conforms and carries the scan field`() {
        scan()
        write("kscope.toml", "[scan]\ninclude = [\"lib/**\"]\ntests = false\n")

        val json = infoJson()

        assertEquals(emptyList(), schema.violations(json))
        val scanField = json.getValue("settings").jsonObject.getValue("scan").jsonObject
        assertEquals(JsonPrimitive("outdated"), scanField.getValue("indexState"))
    }

    @Test
    fun `info of a workspace with an invalid settings file conforms`() {
        write("kscope.toml", "[resources]\nworkers = 99\nlevel = 1\n")

        val json = infoJson()

        assertEquals(emptyList(), schema.violations(json))
        assertTrue(json.getValue("settings").jsonObject.getValue("errors").toString().contains("resources.workers"))
    }

    @Test
    fun `the check fails for a result with a field that the schema does not know or without the settings`() {
        val json = infoJson()

        assertTrue(schema.violations(JsonObject(json + ("surprise" to JsonPrimitive("x")))).isNotEmpty())
        assertTrue(schema.violations(JsonObject(json - "settings")).isNotEmpty())
        val settings = json.getValue("settings").jsonObject
        assertTrue(schema.violations(JsonObject(json + ("settings" to JsonObject(settings - "valid")))).isNotEmpty())
    }
}
