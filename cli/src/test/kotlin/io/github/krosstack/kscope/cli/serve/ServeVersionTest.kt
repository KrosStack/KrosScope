package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.service.KrosScope
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeVersionTest {
    @Test
    fun `another protocol in metadata refuses scans and notes local queries`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val metadata = server.metadata.copy(protocol = 2)
            metadata.write(root)
            val query = runKscope("info", "--workspace", root.toString())
            assertEquals(0, query.statusCode)
            assertEquals(1, query.stderr.trimEnd().lines().size)
            assertEquals(1, runKscope("scan", "--workspace", root.toString()).statusCode)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `another version in the answer prevents remote execution`() = inScannedWorkspace { root ->
        val socket = ServerSocket()
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServeMetadata(0, socket.localPort, "secret", KrosScope.toolVersion(), 1, "then").write(root)
            val listener = executor.submit {
                repeat(2) {
                    socket.accept().use { client ->
                        client.getInputStream().bufferedReader().readLine()
                        val writer = client.getOutputStream().bufferedWriter()
                        writer.write(ServeWire.encode(ServeResponse("hello", toolVersion = "another")) + "\n")
                        writer.flush()
                    }
                }
            }
            val query = runKscope("info", "--workspace", root.toString())
            assertEquals(0, query.statusCode)
            assertTrue(query.stderr.contains("another version"))
            assertEquals(1, runKscope("scan", "--workspace", root.toString()).statusCode)
            listener.get(5, TimeUnit.SECONDS)
            assertTrue(listener.isDone)
        } finally {
            socket.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `a client of another version cannot scan through the server`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val request = ServeRequest(server.metadata.token, listOf("scan"), root.toString(), toolVersion = "another")
            assertEquals(1, ServeClient.exchange(server.metadata, request) {})
            assertEquals(0, server.requestCount)
        }
    }
}
