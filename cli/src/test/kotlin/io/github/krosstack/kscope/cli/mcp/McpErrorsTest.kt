package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpErrorsTest {
    @Test
    fun `legacy versions are echoed and unknown versions fall back`() = inWorkspace { root ->
        for (version in listOf("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25", "unknown")) {
            val (replies, _) = mcpTranscript(root, listOf(initialize(version)))
            val expected = if (version == "unknown") "2025-11-25" else version
            assertEquals(JsonPrimitive(expected), replies.single().getValue("result").jsonObject["protocolVersion"])
        }
    }

    @Test
    fun `modern metadata errors carry exact codes and version data and recover`() = inWorkspace { root ->
        val wrong = buildJsonObject {
            put(
                "_meta",
                buildJsonObject {
                    put("io.modelcontextprotocol/protocolVersion", "2025-11-25")
                    put("io.modelcontextprotocol/clientCapabilities", buildJsonObject {})
                },
            )
        }
        val (replies, _) = mcpTranscript(root, listOf(rpc("ping"), rpc("ping", wrong), modernRpc("ping")))
        assertEquals(-32602, code(replies[0]))
        assertEquals(-32022, code(replies[1]))
        assertEquals(
            buildJsonObject {
                put("supported", JsonArray(listOf(JsonPrimitive("2026-07-28"))))
                put("requested", "2025-11-25")
            },
            replies[1].getValue("error").jsonObject["data"],
        )
        assertEquals(buildJsonObject { put("resultType", "complete") }, replies[2]["result"])
    }

    @Test
    fun `protocol errors never prevent the next request and ids retain their representation`() = inWorkspace { root ->
        val invalid = listOf(
            "not JSON" to -32700,
            "[]" to -32600,
            "{}" to -32600,
            """{"jsonrpc":"1.0","id":1,"method":"ping"}""" to -32600,
            """{"jsonrpc":"2.0","id":true,"method":"ping"}""" to -32600,
            "x".repeat(1048577) to -32600,
            rpc("unknown") to -32601,
            rpc("tools/call", callParams("missing")) to -32602,
            rpc("tools/call") to -32602,
        )
        invalid.forEach { (line, expected) ->
            val next = rpc("ping", id = JsonPrimitive("next"))
            val (replies, _) = mcpTranscript(root, listOf(initialize(), line, next))
            assertEquals(3, replies.size)
            assertEquals(expected, code(replies[1]))
            assertEquals(JsonPrimitive("next"), replies[2]["id"])
            assertEquals(buildJsonObject {}, replies[2]["result"])
            assertEquals(setOf("jsonrpc", "id", "error"), replies[1].keys)
            assertEquals(setOf("code", "message"), replies[1].getValue("error").jsonObject.keys)
        }
        val numeric = rpc("ping").replace("\"id\":1", "\"id\":1.2300")
        val (replies, _) = mcpTranscript(root, listOf(initialize(), numeric, rpc("ping", id = JsonNull)))
        assertEquals("1.2300", replies[1].getValue("id").toString())
        assertEquals(-32600, code(replies[2]))
    }

    @Test
    fun `invalid arguments are tool results and all notifications stay silent`() = inWorkspace { root ->
        for (modern in listOf(false, true)) {
            invalidArguments.forEach { params ->
                val call = if (modern) modernRpc("tools/call", params) else rpc("tools/call", params)
                val ping = if (modern) modernRpc("ping") else rpc("ping")
                val (replies, _) = mcpTranscript(
                    root,
                    listOf(initialize()).takeUnless { modern }.orEmpty() +
                        listOf("""{"jsonrpc":"2.0","method":"unknown"}""", call, ping),
                )
                val result = replies[replies.size - 2].getValue("result").jsonObject
                assertTrue(toolText(result).isNotBlank())
                assertToolResult(result, modern, true, toolText(result))
                assertTrue(replies.last().containsKey("result"))
            }
        }
    }

    private val invalidArguments = listOf(
        callParams("context"),
        callParams("context", buildJsonObject { put("task", 1) }),
        callParams(
            "context",
            buildJsonObject {
                put("task", "x")
                put("extra", true)
            },
        ),
        callParams("context", buildJsonObject { put("task", "x".repeat(20001)) }),
        callParams(
            "context",
            buildJsonObject {
                put("task", "x")
                put("budget", 499)
            },
        ),
        callParams(
            "context",
            buildJsonObject {
                put("task", "x")
                put("budget", 200001)
            },
        ),
        callParams(
            "context",
            buildJsonObject {
                put("task", "x")
                put("budget", 500.5)
            },
        ),
        callParams(
            "analyze",
            buildJsonObject {
                put("query", "x")
                put("detail", "other")
            },
        ),
        callParams("symbol", JsonArray(emptyList())),
        callParams("scan", buildJsonObject { put("--trust-build", true) }),
        callParams("scan", buildJsonObject { put("rebuild", true) }),
        callParams("scan", buildJsonObject { put("anything", "value") }),
        callParams("info", buildJsonObject { put("extra", true) }),
    )

    private fun code(reply: kotlinx.serialization.json.JsonObject): Int =
        reply.getValue("error").jsonObject.getValue("code").toString().toInt()
}
