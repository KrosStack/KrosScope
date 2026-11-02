package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpTranscriptTest {
    private val tools = listOf(
        "context" to buildJsonObject {
            put("task", "change the library")
            put("budget", 500)
        },
        "analyze" to buildJsonObject {
            put("query", "change the library")
            put("detail", "brief")
        },
        "symbol" to buildJsonObject { put("name", "Lib") },
        "impact" to buildJsonObject { put("node", "Lib") },
        "related" to buildJsonObject { put("node", "Lib") },
        "info" to buildJsonObject {},
        "scan" to buildJsonObject {},
    )
    private val commands = listOf(
        listOf("context", "--format", "markdown", "--budget", "500", "--", "change the library"),
        listOf("analyze", "--format", "markdown", "--detail", "brief", "--", "change the library"),
        listOf("symbol", "--format", "markdown", "--", "Lib"),
        listOf("impact", "--format", "markdown", "--", "Lib"),
        listOf("related", "--format", "markdown", "--", "Lib"),
        listOf("info"),
        listOf("scan", "--quiet", "--level", "resolved"),
    )

    @Test
    fun `both eras return exact envelopes and command output for every tool`() = inScannedWorkspace { root ->
        for (modern in listOf(false, true)) verifyTranscript(root, modern)
    }

    private fun verifyTranscript(root: Path, modern: Boolean) {
        val opening = if (modern) rpc("server/discover") else initialize()
        val request = if (modern) ::modernRpc else { method, params -> rpc(method, params) }
        val lines = listOf(opening, """{"jsonrpc":"2.0","method":"notifications/initialized"}""") +
            listOf(request("ping", buildJsonObject {}), request("tools/list", buildJsonObject {})) +
            tools.map { (name, args) -> request("tools/call", callParams(name, args)) }
        val (replies, _) = mcpTranscript(root, lines, McpSettings(noServer = true, scanRunner = stableMcpScan))
        assertEquals(10, replies.size)
        replies.forEach {
            assertEquals(setOf("jsonrpc", "id", "result"), it.keys)
            assertEquals(JsonPrimitive("2.0"), it["jsonrpc"])
            assertEquals(JsonPrimitive(1), it["id"])
        }
        assertOpening(replies.first().getValue("result").jsonObject, modern)
        val ping = buildJsonObject { if (modern) put("resultType", "complete") }
        assertEquals(ping, replies[1]["result"])
        assertCatalogue(replies[2].getValue("result").jsonObject, modern)
        commands.forEachIndexed { index, command ->
            val stdout = mcpCommandStdout(root, command)
            assertToolResult(replies[index + 3].getValue("result").jsonObject, modern, false, stdout)
        }
    }

    private fun assertOpening(result: kotlinx.serialization.json.JsonObject, modern: Boolean) {
        val info = buildJsonObject {
            put("name", "kscope")
            put("version", KrosScope.toolVersion())
        }
        val expected = buildJsonObject {
            if (modern) {
                put("resultType", "complete")
                put("supportedVersions", JsonArray(listOf(JsonPrimitive("2026-07-28"))))
                put("_meta", buildJsonObject { put("io.modelcontextprotocol/serverInfo", info) })
                put("ttlMs", 3600000)
                put("cacheScope", "private")
            } else {
                put("protocolVersion", "2025-11-25")
                put("serverInfo", info)
            }
            put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
            put("instructions", McpProtocol.INSTRUCTIONS)
        }
        assertEquals(expected, result)
        assertTrue(McpProtocol.INSTRUCTIONS.contains("before reading files"))
    }

    private fun assertCatalogue(result: kotlinx.serialization.json.JsonObject, modern: Boolean) {
        val expected = setOf("tools") + if (modern) setOf("resultType", "ttlMs", "cacheScope") else emptySet()
        assertEquals(expected, result.keys)
        if (modern) {
            assertEquals(JsonPrimitive("complete"), result["resultType"])
            assertEquals(JsonPrimitive(3600000), result["ttlMs"])
            assertEquals(JsonPrimitive("private"), result["cacheScope"])
        }
        val tools = result.getValue("tools") as JsonArray
        assertEquals(
            listOf("context", "analyze", "symbol", "impact", "related", "info", "scan"),
            tools.map { it.jsonObject.getValue("name").toString().trim('"') },
        )
        tools.forEach { element ->
            val tool = element.jsonObject
            assertEquals(setOf("name", "title", "description", "inputSchema"), tool.keys)
            assertTrue((tool.getValue("title") as JsonPrimitive).content.isNotBlank())
            assertTrue((tool.getValue("description") as JsonPrimitive).content.isNotBlank())
            assertSchema((tool.getValue("name") as JsonPrimitive).content, tool.getValue("inputSchema").jsonObject)
        }
    }

    private fun assertSchema(name: String, schema: JsonObject) {
        val required = when (name) {
            "context" -> listOf("task")
            "analyze" -> listOf("query")
            "symbol" -> listOf("name")
            "impact", "related" -> listOf("node")
            else -> emptyList()
        }
        val optional = when (name) {
            "context" -> listOf("budget")
            "analyze" -> listOf("detail")
            else -> emptyList()
        }
        assertEquals(setOf("type", "additionalProperties", "properties", "required"), schema.keys)
        assertEquals(JsonPrimitive("object"), schema["type"])
        assertEquals(JsonPrimitive(false), schema["additionalProperties"])
        assertEquals(JsonArray(required.map(::JsonPrimitive)), schema["required"])
        val properties = schema.getValue("properties").jsonObject
        assertEquals((required + optional).toSet(), properties.keys)
        properties.forEach { (key, field) ->
            val property = field.jsonObject
            assertTrue((property.getValue("description") as JsonPrimitive).content.isNotBlank())
            assertEquals(expectedProperty(key), JsonObject(property - "description"))
        }
    }

    private fun expectedProperty(key: String): JsonObject = buildJsonObject {
        if (key == "budget") {
            put("type", "integer")
            put("minimum", 500)
            put("maximum", 200000)
        } else {
            put("type", "string")
            put("maxLength", 20000)
            if (key == "detail") put("enum", JsonArray(listOf("brief", "standard", "full").map(::JsonPrimitive)))
        }
    }
}
