package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inWorkspace
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpEraTest {
    @Test
    fun `a client that probes with discover and then initializes is served the earlier protocol`() =
        inWorkspace { root ->
            val (replies, _) = mcpTranscript(root, listOf(rpc("server/discover"), initialize(), rpc("tools/list")))

            assertEquals(3, replies.size)
            val discovered = replies[0].getValue("result").jsonObject
            assertEquals(JsonPrimitive("complete"), discovered["resultType"])
            val initialized = replies[1].getValue("result").jsonObject
            assertEquals(JsonPrimitive("2025-11-25"), initialized["protocolVersion"])
            val listed = replies[2].getValue("result").jsonObject
            assertTrue(listed.getValue("tools") is JsonArray)
            assertFalse("resultType" in listed, "an earlier protocol has no result type")
        }

    @Test
    fun `without initialize a request needs the metadata of the current protocol`() = inWorkspace { root ->
        val requests = listOf(rpc("server/discover"), rpc("tools/list"), modernRpc("tools/list"))
        val (replies, _) = mcpTranscript(root, requests)

        assertEquals(3, replies.size)
        assertEquals(JsonPrimitive(-32602), replies[1].getValue("error").jsonObject["code"])
        assertEquals(JsonPrimitive("complete"), replies[2].getValue("result").jsonObject["resultType"])
    }

    @Test
    fun `discover is answered after initialize as well`() = inWorkspace { root ->
        val (replies, _) = mcpTranscript(root, listOf(initialize(), rpc("server/discover")))

        val supported = replies[1].getValue("result").jsonObject.getValue("supportedVersions") as JsonArray
        assertEquals(listOf(JsonPrimitive("2026-07-28")), supported.toList())
    }
}
