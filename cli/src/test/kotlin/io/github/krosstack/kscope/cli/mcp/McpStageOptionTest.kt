package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class McpStageOptionTest {
    @Test
    fun `lexical stage executes locally through mcp`() = inScannedWorkspace { root ->
        val arguments = buildJsonObject {
            put("query", "change Lib")
            put("stage", "lexical")
        }
        val (replies, _) = mcpTranscript(
            root,
            listOf(initialize(), rpc("tools/call", callParams("analyze", arguments))),
            McpSettings(noServer = true),
        )

        assertEquals(JsonPrimitive(false), replies.last().getValue("result").jsonObject["isError"])
    }

    @Test
    fun `mcp translates the stage to a cli option`() {
        val arguments = buildJsonObject {
            put("query", "change Lib")
            put("stage", "lexical")
        }
        var forwarded = emptyList<String>()
        McpTools.call("analyze", arguments) {
            forwarded = it
            McpToolResult("analysis")
        }

        assertEquals(listOf("analyze", "--format", "markdown", "--stage", "lexical", "--", "change Lib"), forwarded)
    }

    @Test
    fun `mcp forwards lexical stage through a running server`() = inScannedWorkspace { root ->
        val arguments = buildJsonObject {
            put("query", "change Lib")
            put("stage", "lexical")
        }
        val requests = mutableListOf<ServeRequest>()
        val settings = McpSettings(serve = ServeSettings(onRequest = { requests.add(it) }))
        mcpTranscript(root, listOf(initialize(), rpc("tools/call", callParams("analyze", arguments))), settings)

        assertEquals(
            listOf(listOf("--stage", "lexical")),
            requests.single().arguments.windowed(2).filter { it.first() == "--stage" },
        )
    }

    @Test
    fun `unknown mcp stage is rejected before cli execution`() {
        val arguments = buildJsonObject {
            put("query", "change Lib")
            put("stage", "unknown")
        }
        val result = McpTools.call("analyze", arguments) {
            error("invalid stage reached execution")
        }

        assertEquals(McpToolResult("Argument stage must be one of full, lexical", true), result)
    }
}
