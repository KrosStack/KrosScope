package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpSafetyTest {
    @Test
    fun `option and argument file prefixes reach commands as literal text`() = inScannedWorkspace { root ->
        for (task in listOf("--trust-build", "-h", "@file", "--workspace=/elsewhere", "@@file", "café 中文\nnext")) {
            val params = callParams(
                "context",
                buildJsonObject {
                    put("task", task)
                    put("budget", 500)
                },
            )
            val local = mcpCommandStdout(
                root,
                listOf(
                    "--no-server",
                    "context",
                    "--format",
                    "markdown",
                    "--budget",
                    "500",
                    "--",
                    task,
                ),
            )
            for (noServer in listOf(false, true)) {
                val settings = McpSettings(
                    noServer = noServer,
                    serve = ServeSettings(onRequest = {
                        assertEquals(listOf("--", task), it.arguments.takeLast(2))
                    }),
                )
                val (replies, _) = mcpTranscript(root, listOf(initialize(), rpc("tools/call", params)), settings)
                assertToolResult(replies.last().getValue("result").jsonObject, false, false, local)
            }
        }
    }

    @Test
    fun `scans never receive build trust rebuild or verify through either execution path`() =
        inScannedWorkspace { root ->
            var scans = 0
            val runner = ScanRunner { workspace, options, rebuild, verify ->
                scans++
                assertFalse(options.trustBuild)
                assertFalse(rebuild)
                assertFalse(verify)
                KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }
            }
            for (noServer in listOf(false, true)) {
                val lines = listOf(
                    initialize(),
                    rpc("tools/call", callParams("scan")),
                    rpc("tools/call", callParams("scan", buildJsonObject { put("trustBuild", true) })),
                )
                val (replies, _) = mcpTranscript(root, lines, McpSettings(noServer = noServer, scanRunner = runner))
                assertToolResult(
                    replies[1].getValue("result").jsonObject,
                    false,
                    false,
                    toolText(replies[1].getValue("result").jsonObject),
                )
                assertEquals("true", replies[2].getValue("result").jsonObject.getValue("isError").toString())
            }
            assertEquals(2, scans)
        }

    @Test
    fun `stdout contains only rpc while failures go to stderr and the next tool still works`() =
        inScannedWorkspace { root ->
            for (failure in listOf(IOException("scan failed"), IllegalStateException("unexpected scan failure"))) {
                val runner = ScanRunner { _, _, _, _ -> throw failure }
                val (replies, error) = mcpTranscript(
                    root,
                    listOf(
                        initialize(),
                        rpc("tools/call", callParams("scan")),
                        rpc("tools/call", callParams("info")),
                    ),
                    McpSettings(scanRunner = runner),
                )
                assertEquals(3, replies.size)
                assertTrue(error.contains("Serving"))
                assertTrue(error.contains(failure.message.orEmpty()))
                assertToolResult(
                    replies[1].getValue("result").jsonObject,
                    false,
                    true,
                    toolText(replies[1].getValue("result").jsonObject),
                )
                assertTrue(toolText(replies[1].getValue("result").jsonObject).contains(failure.message.orEmpty()))
                assertEquals("false", replies[2].getValue("result").jsonObject.getValue("isError").toString())
            }
        }
}
