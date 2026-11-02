package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeLock
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.watch.WatchReason
import io.github.krosstack.kscope.cli.serve.watch.WatchServerInputs
import io.github.krosstack.kscope.cli.serve.watch.WatchSettings
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.model.Fingerprint
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpLifecycleTest {
    @Test
    fun `embedded server serves shell queries and releases metadata and lock at eof`() = inScannedWorkspace { root ->
        var inspected = false
        val input = object : ByteArrayInputStream((initialize() + "\n").toByteArray()) {
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (!inspected) {
                    inspected = true
                    assertTrue(Files.exists(ServeMetadata.file(root)))
                    val metadata = checkNotNull(ServeMetadata.read(root))
                    assertTrue(ServeClient.alive(metadata))
                    val local = runAt(root, listOf("--no-server", "info"))
                    val shell = runAt(root, listOf("info"))
                    assertEquals(0, shell.statusCode)
                    assertEquals(local.stdout, shell.stdout)
                }
                return super.read(bytes, offset, length)
            }
        }
        val output = StringWriter()
        McpSession(root, McpStreams(input, output, StringWriter())).run()
        assertTrue(inspected)
        assertFalse(Files.exists(ServeMetadata.file(root)))
        ServeLock.acquire(root).use { assertTrue(parseJson(output.toString()).containsKey("result")) }
    }

    @Test
    fun `existing server answers tools and survives the mcp session`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val (replies, error) = mcpTranscript(
                root,
                listOf(
                    initialize(),
                    rpc("tools/call", callParams("info")),
                ),
                McpSettings(),
            )
            assertEquals(1, server.requestCount)
            assertEquals(server.metadata, ServeMetadata.read(root))
            assertFalse(error.contains("Serving"))
            assertEquals("false", replies.last().getValue("result").jsonObject.getValue("isError").toString())
        }
    }

    @Test
    fun `no server creates no metadata and does not use an existing server`() = inScannedWorkspace { root ->
        val lines = listOf(initialize(), rpc("tools/call", callParams("info")))
        val (replies, _) = mcpTranscript(root, lines)
        assertFalse(Files.exists(ServeMetadata.file(root)))
        assertEquals("false", replies.last().getValue("result").jsonObject.getValue("isError").toString())
        withTestServer(root) { server ->
            val (local, _) = mcpTranscript(root, lines)
            assertEquals(0, server.requestCount)
            assertEquals(replies, local)
        }
    }

    @Test
    fun `watch is passed to the embedded server and index behind notices join tool text`() =
        inScannedWorkspace { root ->
            val settings = McpSettings(
                serve = ServeSettings(
                    watchEnabled = true,
                    watch = WatchSettings(stamp = { Fingerprint("stable") }),
                ),
            )
            val params = callParams(
                "context",
                buildJsonObject {
                    put("task", "change library")
                    put("budget", 500)
                },
            )
            val (replies, error) = mcpTranscript(root, listOf(initialize(), rpc("tools/call", params)), settings)
            assertTrue(error.contains("Watching"))
            assertEquals("false", replies.last().getValue("result").jsonObject.getValue("isError").toString())
            assertFalse(Files.exists(ServeMetadata.file(root)))
        }

    @Test
    fun `nonprogress notices follow unchanged stdout after a blank line`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        withTestServer(root, inputs.settings()) { server ->
            inputs.awaitFirstPoll()
            server.query(listOf("scan", "--quiet"))
            inputs.poll(server, 0)
            val (replies, error) = mcpTranscript(
                root,
                listOf(initialize(), rpc("tools/call", callParams("info"))),
                McpSettings(),
            )
            val notice = "Index is behind: ${WatchReason.SETTLING.text}.\n"
            val expected = mcpCommandStdout(root, listOf("info")) + "\n" + notice
            assertToolResult(replies.last().getValue("result").jsonObject, false, false, expected)
            assertEquals(notice, error)
        }
    }
}
