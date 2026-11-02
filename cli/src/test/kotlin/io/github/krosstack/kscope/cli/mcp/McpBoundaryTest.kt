package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpBoundaryTest {
    @Test
    fun `malformed utf8 is a parse error and the next request survives`() {
        val prefix = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"".toByteArray()
        val invalid = prefix + byteArrayOf(0xC0.toByte(), 0xAF.toByte()) + "\"}\n".toByteArray()
        val input = initialize().toByteArray() + byteArrayOf(10) + invalid + (rpc("ping") + "\n").toByteArray()
        val output = StringWriter()
        McpProtocol(McpStreams(ByteArrayInputStream(input), output, StringWriter())) { _, _ ->
            McpToolResult("unused")
        }.run()
        val replies = output.toString().lines().dropLast(1).map(::parseJson)
        assertEquals(JsonPrimitive(-32700), replies[1].getValue("error").jsonObject["code"])
        assertEquals(buildJsonObject {}, replies[2]["result"])
    }

    @Test
    fun `bare literals malformed numbers and raw string controls are parse errors`() = inWorkspace { root ->
        for (line in listOf(
            "garbage",
            "NaN",
            "01",
            "1.",
            "{foo:1}",
            "{\"x\":01}",
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"pi\tng\"}",
        )) {
            val (replies, _) = mcpTranscript(root, listOf(initialize(), line, rpc("ping")))
            assertEquals(JsonPrimitive(-32700), replies[1].getValue("error").jsonObject["code"])
            assertEquals(setOf("jsonrpc", "id", "error"), replies[1].keys)
            assertEquals(buildJsonObject {}, replies[2]["result"])
        }
    }

    @Test
    fun `unexpected tool failures remain tool results and preserve the next request`() {
        for (modern in listOf(false, true)) {
            val request = if (modern) ::modernRpc else { method, params -> rpc(method, params) }
            val lines = listOf(
                if (modern) rpc("server/discover") else initialize(),
                request("tools/call", callParams("info")),
                request("ping", buildJsonObject {}),
            )
            val output = StringWriter()
            val error = StringWriter()
            val input = ByteArrayInputStream((lines.joinToString("\n") + "\n").toByteArray())
            McpProtocol(McpStreams(input, output, error)) { _, _ -> error("unexpected failure") }.run()
            val replies = output.toString().lines().dropLast(1).map(::parseJson)
            assertToolResult(replies[1].getValue("result").jsonObject, modern, true, "error: unexpected failure")
            assertTrue(replies[2].containsKey("result"))
            assertEquals("error: unexpected failure\n", error.toString())
        }
    }

    @Test
    fun `modern unknown methods and tools have protocol errors while malformed notifications stay silent`() =
        inWorkspace { root ->
            val cases = listOf(
                modernRpc("unknown") to -32601,
                modernRpc("tools/call", callParams("unknown")) to -32602,
                modernRpc("tools/call", buildJsonObject { put("name", 1) }) to -32602,
                modernRpc("tools/call", buildJsonObject {}) to -32602,
            )
            for ((line, code) in cases) {
                val notification = """{"jsonrpc":"2.0","method":"tools/call","params":[]}"""
                val (replies, _) = mcpTranscript(
                    root,
                    listOf(
                        rpc("server/discover"),
                        notification,
                        line,
                        modernRpc("ping"),
                    ),
                )
                assertEquals(3, replies.size)
                assertEquals(JsonPrimitive(code), replies[1].getValue("error").jsonObject["code"])
                assertEquals(buildJsonObject { put("resultType", "complete") }, replies[2]["result"])
            }
        }

    @Test
    fun `each modern metadata property is required and capabilities must be an object`() = inWorkspace { root ->
        val invalid = listOf(
            buildJsonObject { put("io.modelcontextprotocol/protocolVersion", "2026-07-28") },
            buildJsonObject { put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {}) },
            buildJsonObject {
                put("io.modelcontextprotocol/protocolVersion", "2026-07-28")
                put("io.modelcontextprotocol/clientCapabilities", JsonArray(emptyList()))
            },
        )
        invalid.forEach { meta ->
            val (replies, _) = mcpTranscript(
                root,
                listOf(
                    rpc("ping", buildJsonObject { put("_meta", meta) }),
                    modernRpc("ping"),
                ),
            )
            assertEquals(JsonPrimitive(-32602), replies[0].getValue("error").jsonObject["code"])
            assertEquals(setOf("code", "message"), replies[0].getValue("error").jsonObject.keys)
            assertTrue(replies[1].containsKey("result"))
        }
    }
}
