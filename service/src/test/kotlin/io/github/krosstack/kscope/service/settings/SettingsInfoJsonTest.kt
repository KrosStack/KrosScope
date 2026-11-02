package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.service.ServiceJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** The `settings` field of the JSON result of `info`: one addition, in a fixed shape. */
class SettingsInfoJsonTest {
    private val settingsFile = "kscope.toml"

    private fun infoJson(workspace: SettingsWorkspace): JsonObject {
        val info = workspace.open { it.info() }
        return Json.parseToJsonElement(ServiceJson.encode(info)).jsonObject
    }

    private fun JsonObject.at(vararg path: String): JsonElement =
        path.fold<String, JsonElement>(this) { node, key -> node.jsonObject.getValue(key) }

    @Test
    fun `the json result has the additional fields settings and level and no existing field changes`() {
        SettingsWorkspace().use { workspace ->
            workspace.open { it.scan() }

            val json = infoJson(workspace)

            assertEquals(
                listOf(
                    "schemaVersion", "indexLevel", "toolVersion", "indexLocation", "indexed", "structure",
                    "artifactsByType", "symbolsByKind", "coverage", "diagnostics", "metricsByName",
                    "searchIndexPopulated", "settings",
                ),
                json.keys.toList(),
            )
            assertEquals(1, json.at("schemaVersion").jsonPrimitive.int)
        }
    }

    @Test
    fun `the settings object has the file the validity the errors and the resources`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "[resources]\nworkers = 4\n")

            val json = infoJson(workspace)

            val keys = json.at("settings").jsonObject.keys.toList()
            assertEquals(listOf("file", "valid", "errors", "resources", "scan", "analyzers", "level"), keys)
            assertEquals(
                listOf("include", "exclude", "tests", "generated", "indexState"),
                json.at("settings", "scan").jsonObject.keys.toList(),
            )
            assertEquals("not-indexed", json.at("settings", "scan", "indexState").jsonPrimitive.content)
            val switches = listOf("androidResources", "aidl", "schemas", "nativeBindings", "gitHistory")
            assertEquals(
                switches + listOf("composeResources", "webBundles", "indexState"),
                json.at("settings", "analyzers").jsonObject.keys.toList(),
            )
            assertEquals("kscope.toml", json.at("settings", "file").jsonPrimitive.content)
            assertEquals("true", json.at("settings", "valid").jsonPrimitive.content)
            assertEquals(JsonArray(emptyList()), json.at("settings", "errors"))
            assertEquals(listOf("workers", "workerHeapMib"), json.at("settings", "resources").jsonObject.keys.toList())
            val workers = json.at("settings", "resources", "workers").jsonObject
            assertEquals(listOf("value", "origin"), workers.keys.toList())
            assertEquals(4, workers.at("value").jsonPrimitive.int)
            assertEquals("kscope.toml", workers.at("origin").jsonPrimitive.content)
            assertEquals("default", json.at("settings", "resources", "workerHeapMib", "origin").jsonPrimitive.content)
        }
    }

    @Test
    fun `without a file the json holds null for the file and for a worker count that is decided at scan time`() {
        SettingsWorkspace().use { workspace ->
            val json = infoJson(workspace)

            assertEquals(JsonNull, json.at("settings", "file"))
            assertEquals(JsonNull, json.at("settings", "resources", "workers", "value"))
            assertEquals(2048, json.at("settings", "resources", "workerHeapMib", "value").jsonPrimitive.int)
        }
    }

    @Test
    fun `an invalid file shows its errors with line key and message`() {
        SettingsWorkspace().use { workspace ->
            workspace.write(settingsFile, "format = 1\n\n[resources]\nworkers = 99\nlevel = 2\n")

            val json = infoJson(workspace)

            assertEquals("false", json.at("settings", "valid").jsonPrimitive.content)
            val errors = json.at("settings", "errors").jsonArray.map { it.jsonObject }
            assertEquals(listOf("line", "key", "message"), errors.first().keys.toList())
            assertEquals(listOf(4, 5), errors.map { it.at("line").jsonPrimitive.int })
            val keys = errors.map { it.at("key").jsonPrimitive.content }
            assertEquals(listOf("resources.workers", "resources.level"), keys)
            assertEquals("must be between 1 and 8", errors.first().at("message").jsonPrimitive.content)
        }
    }

    @Test
    fun `an error about the file as a whole has no line and no key`() {
        SettingsWorkspace().use { workspace ->
            Files.createDirectory(workspace.root.resolve(settingsFile))

            val error = infoJson(workspace).at("settings", "errors").jsonArray.single().jsonObject

            assertEquals(JsonNull, error.at("line"))
            assertEquals(JsonNull, error.at("key"))
            assertEquals("must be a regular file", error.at("message").jsonPrimitive.content)
        }
    }
}
