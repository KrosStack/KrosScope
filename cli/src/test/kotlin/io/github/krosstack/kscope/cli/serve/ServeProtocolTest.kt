package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import java.net.Socket
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeProtocolTest {
    @Test
    fun `bad tokens oversized lines and silent connections close without an answer`() = inScannedWorkspace { root ->
        val server = ServeServer.open(root, ServeSettings(requestTimeoutMillis = 1_500))
        val executor = Executors.newSingleThreadExecutor()
        val running = executor.submit { server.run() }
        try {
            val request = ServeRequest(token = "wrong", arguments = listOf("info"), directory = root.toString())
            assertClosed(server, ServeWire.encode(request))
            assertClosed(server, "{\"arguments\":[\"info\"],\"directory\":\"unused\"}")
            assertClosed(server, "x".repeat(ServeWire.MAX_REQUEST_BYTES + 1))
            server.connect().use { socket ->
                socket.soTimeout = 60_000
                assertEquals(-1, socket.getInputStream().read())
            }
            assertEquals(0, server.query(listOf("info")).exitCode)
        } finally {
            server.requestStop()
            running.get(60, TimeUnit.SECONDS)
            executor.shutdownNow()
            server.close()
        }
    }

    @Test
    fun `the server refuses structure and rejects unserved commands`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            for (args in listOf(listOf("doctor"), listOf("info", "--structure"), listOf("serve"))) {
                val answer = server.query(args)
                assertEquals(2, answer.exitCode)
                if ("--structure" in args) {
                    assertTrue(answer.stderr.contains("not served"))
                    assertEquals(1, answer.stderr.trimEnd().lines().size)
                } else {
                    assertTrue(answer.stderr.contains(args.first()))
                }
            }
            assertEquals(0, server.query(listOf("info")).exitCode)
        }
    }

    private fun assertClosed(server: ServeServer, line: String) {
        server.connect().use { socket ->
            socket.soTimeout = 60_000
            runCatching { socket.getOutputStream().write((line + "\n").toByteArray(Charsets.UTF_8)) }
            assertEquals(-1, socket.getInputStream().read())
        }
    }
}

internal fun ServeServer.connect(): Socket = Socket(boundAddress, metadata.port)

internal fun ServeServer.query(arguments: List<String>): ServeAnswer {
    val stdout = StringBuilder()
    val stderr = StringBuilder()
    val request = ServeRequest(
        metadata.token,
        arguments + listOf("--workspace", workspace.toString()),
        Path.of("").toAbsolutePath().toString(),
    )
    val code = ServeClient.exchange(metadata, request) { chunk ->
        if (chunk.stream == "stdout") stdout.append(chunk.text) else stderr.append(chunk.text)
    }
    return ServeAnswer(stdout.toString(), stderr.toString(), checkNotNull(code))
}

internal data class ServeAnswer(val stdout: String, val stderr: String, val exitCode: Int)
