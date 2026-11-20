package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.semantic.SemanticQueryFixture
import io.github.krosstack.kscope.retrieval.MlMode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpContextMlTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `context ml false reaches local and embedded service requests`() {
        val fixture = SemanticQueryFixture(directory)
        for (noServer in listOf(true, false)) {
            val served = mutableListOf<ServeRequest>()
            val settings = McpSettings(
                noServer = noServer,
                scanRunner = fixture,
                serve = ServeSettings(onRequest = { served += it }),
            )
            val results = call(fixture, settings, listOf(null, false, true))
            assertNotEquals(fixture.structural("markdown", System.lineSeparator()), toolText(results.first()))
            assertEquals(fixture.structural("markdown", System.lineSeparator()), toolText(results[1]))
            assertEquals(toolText(results.first()), toolText(results.last()))
            assertEquals(if (noServer) 0 else 3, served.size)
            if (!noServer) assertTrue(served[1].arguments.windowed(2).contains(listOf("--ml", "off")))
        }
        assertEquals(fixture.fake.workersStarted, fixture.fake.workersClosed)
    }

    @Test
    fun `session no ml overrides tool ml true`() {
        val fixture = SemanticQueryFixture(directory)
        for (noServer in listOf(true, false)) {
            val settings = McpSettings(
                noServer = noServer,
                scanRunner = fixture,
                serve = ServeSettings(ml = MlMode.OFF),
            )
            val starts = fixture.fake.workersStarted
            val results = call(fixture, settings, listOf(true, null))
            results.forEach { assertEquals(fixture.structural("markdown", System.lineSeparator()), toolText(it)) }
            assertEquals(starts, fixture.fake.workersStarted)
            assertEquals(0, fixture.fake.queriesEmbedded)
        }
    }

    @Test
    fun `invalid context ml values fail before execution`() {
        val invalid = listOf(
            JsonPrimitive("false"),
            JsonPrimitive("on"),
            JsonPrimitive(0),
            JsonNull,
            JsonArray(emptyList()),
            JsonObject(emptyMap()),
        )
        for (value in invalid) {
            val arguments = buildJsonObject {
                put("task", "anchor")
                put("ml", value)
            }
            val result = McpTools.call("context", arguments) { error("Invalid arguments must not execute") }
            val message = if (value is JsonPrimitive) {
                "Argument ml must be a boolean"
            } else {
                "Argument ml has the wrong type"
            }
            assertEquals(McpToolResult(message, true), result)
        }
        val context = McpTools.catalogue.first { it.jsonObject["name"] == JsonPrimitive("context") }.jsonObject
        val schema = context.getValue("inputSchema").jsonObject
        val ml = schema.getValue("properties").jsonObject.getValue("ml").jsonObject
        assertEquals(JsonPrimitive("boolean"), ml["type"])
        assertEquals(JsonPrimitive(true), ml["default"])
        assertEquals(JsonArray(listOf(JsonPrimitive("task"))), schema["required"])
    }

    private fun call(fixture: SemanticQueryFixture, settings: McpSettings, modes: List<Boolean?>): List<JsonObject> {
        val calls = modes.map { mode ->
            val arguments = buildJsonObject {
                put("task", "anchor")
                put("budget", 500)
                mode?.let { put("ml", it) }
            }
            rpc("tools/call", callParams("context", arguments))
        }
        val (replies, _) = mcpTranscript(fixture.root, listOf(initialize()) + calls, settings)
        return replies.drop(1).map { it.getValue("result").jsonObject }.onEach {
            assertEquals(JsonPrimitive(false), it["isError"], toolText(it))
        }
    }
}
