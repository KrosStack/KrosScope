package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeSettings
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpMlOptionTest {
    @Test
    fun `ml false reports off locally and through a running server`() = inScannedWorkspace { root ->
        val arguments = buildJsonObject {
            put("query", "change Lib")
            put("ml", false)
        }
        val requests = listOf(initialize(), rpc("tools/call", callParams("analyze", arguments)))
        for (noServer in listOf(true, false)) {
            val servedRequests = mutableListOf<ServeRequest>()
            val settings = McpSettings(
                noServer = noServer,
                serve = ServeSettings(onRequest = { servedRequests.add(it) }),
            )
            val (replies, _) = mcpTranscript(root, requests, settings)
            val result = replies.last().getValue("result").jsonObject

            assertEquals(JsonPrimitive(false), result["isError"])
            assertEquals(if (noServer) 0 else 1, servedRequests.size)
            if (!noServer) {
                assertTrue(servedRequests.single().arguments.windowed(2).contains(listOf("--ml", "off")))
            }
        }
    }

    @Test
    fun `boolean defaults and explicit on reach the cli unchanged`() {
        val options = listOf(buildJsonObject {}, buildJsonObject { put("ml", true) })
        for (option in options) {
            val arguments = buildJsonObject { put("query", "change Lib") }
            val result = McpTools.call("analyze", JsonObject(arguments + option)) { command ->
                assertEquals(option.isNotEmpty(), "--ml" in command)
                if (option.isNotEmpty()) assertTrue(command.windowed(2).contains(listOf("--ml", "on")))
                McpToolResult("analysis")
            }

            assertEquals(McpToolResult("analysis"), result)
        }
    }

    @Test
    fun `ml rejects strings numbers null and undefined properties before execution`() = inWorkspace { root ->
        val invalid = listOf(JsonPrimitive("no"), JsonPrimitive("false"), JsonPrimitive(0), JsonNull)
        for (value in invalid) {
            val arguments = buildJsonObject {
                put("query", "change Lib")
                put("ml", value)
            }
            val (replies, _) = mcpTranscript(
                root,
                listOf(initialize(), rpc("tools/call", callParams("analyze", arguments))),
            )
            val result = replies.last().getValue("result").jsonObject

            assertEquals(JsonPrimitive(true), result["isError"])
            assertEquals("Argument ml must be a boolean", toolText(result))
        }
    }

    @Test
    fun `ml on another tool is an unknown argument`() {
        var executed = false
        val result = McpTools.call("context", buildJsonObject { put("ml", false) }) {
            executed = true
            McpToolResult("unexpected")
        }
        assertFalse(executed)
        assertEquals(McpToolResult("Unknown argument: ml", true), result)
    }
}
