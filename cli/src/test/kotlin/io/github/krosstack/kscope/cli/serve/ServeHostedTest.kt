package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.KrosScope
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeHostedTest {
    private val refusal = "This workspace is kept by the service. " +
        "Run `kscope service remove` for it, or `kscope service stop`."

    @Test
    fun `a hosted server refuses stop with one line and keeps answering`() = inScannedWorkspace { root ->
        withTestServer(root, ServeSettings(hosted = true)) { server ->
            val stop = runKscope("serve", "--stop", "--workspace", root.toString())

            assertEquals(1, stop.statusCode, stop.output)
            assertEquals("", stop.stdout)
            assertEquals(refusal, stop.stderr.trimEnd())
            assertTrue(Files.exists(ServeMetadata.file(root)))
            assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
            assertEquals(1, server.requestCount)
            assertTrue(runKscope("serve", "--status", "--workspace", root.toString()).stdout.contains("Server runs"))
        }
    }

    @Test
    fun `a server that is not hosted still stops without a word and with exit code 0`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val stop = runKscope("serve", "--stop", "--workspace", root.toString())

            server.awaitTermination()
            assertEquals(0, stop.statusCode, stop.output)
            assertEquals("", stop.output)
            assertFalse(Files.exists(ServeMetadata.file(root)))
        }
    }

    @Test
    fun `stop without a server still says so and exits with 0`() = inScannedWorkspace { root ->
        val stop = runKscope("serve", "--stop", "--workspace", root.toString())

        assertEquals(0, stop.statusCode, stop.output)
        assertEquals("No server runs for this workspace.", stop.stdout.trimEnd())
    }

    @Test
    fun `stop exits with 0 when the server closes the connection before it sends an exit`() = inWorkspace { root ->
        ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1")).use { listener ->
            val hangUp = Thread({
                listener.accept().use { socket ->
                    socket.getInputStream().bufferedReader().readLine()
                    socket.getOutputStream().write((ServeWire.encode(ServeResponse("hello")) + "\n").toByteArray())
                }
            }, "hello-then-hang-up")
            hangUp.start()
            val port = listener.localPort
            ServeMetadata(ProcessHandle.current().pid(), port, "token", KrosScope.toolVersion(), 1, "then").write(root)

            val stop = runKscope("serve", "--stop", "--workspace", root.toString())

            hangUp.join()
            assertEquals(0, stop.statusCode, stop.output)
            assertEquals("", stop.output)
        }
    }

    private companion object {
        const val BACKLOG = 50
    }
}
