package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeCommandTreeTest {
    @Test
    fun `server help lists only served commands`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val answer = server.query(listOf("--help"))
            assertEquals(0, answer.exitCode)
            for (command in listOf("doctor", "benchmark", "serve")) {
                assertFalse(Regex("(?m)^\\s+$command\\s").containsMatchIn(answer.stdout))
            }
            assertTrue(answer.stdout.contains("scan"))
        }
    }

    @Test
    fun `unserved commands are unknown to the parser after global options`() = inScannedWorkspace { root ->
        Files.createDirectory(root.resolve("info"))
        withTestServer(root) { server ->
            for (command in listOf("doctor", "benchmark", "serve")) {
                val answer = server.query(listOf("--workspace", root.resolve("info").toString(), command))
                assertEquals(2, answer.exitCode)
                assertTrue(answer.stderr.contains(command))
                assertFalse(answer.stderr.contains("not served"))
            }
        }
    }

    @Test
    fun `expanded arguments beginning with at are literal on the server`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val local = runKscope("symbol", "@@missing", "--no-server", "--workspace", root.toString())
            val remote = server.query(listOf("symbol", "@missing"))
            assertEquals(local.statusCode, remote.exitCode)
            assertEquals(local.stdout, remote.stdout)
            assertEquals(local.stderr, remote.stderr)
            val client = runKscope("symbol", "@@missing", "--workspace", root.toString())
            assertEquals(local.stdout, client.stdout)
            val literal = runAt(root, listOf("--no-server", "symbol", "--", "@@missing"))
            assertEquals(literal.stdout, runAt(root, listOf("symbol", "--", "@@missing")).stdout)
        }
    }

    @Test
    fun `structure refusal uses the parsed flag rather than an option value`() = inWorkspace { parent ->
        val root = Files.createDirectory(parent.resolve("--structure"))
        writeFile(root, "README.md", "# Sample\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        withTestServer(root) { server ->
            val request = ServeRequest(
                server.metadata.token,
                listOf("info", "--workspace", "--structure"),
                parent.toString(),
            )
            assertEquals(0, ServeClient.exchange(server.metadata, request) {})
            assertEquals(2, server.query(listOf("info", "--structure")).exitCode)
        }
    }
}
