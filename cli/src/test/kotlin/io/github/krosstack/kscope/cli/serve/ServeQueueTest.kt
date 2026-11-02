package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeQueueTest {
    @Test
    fun `only sixteen authenticated requests can wait behind a running request`() = inScannedWorkspace { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queued = CountDownLatch(17)
        val settings = ServeSettings(
            onRequest = {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            },
        )
        val server = ServeServer.open(root, settings, onQueued = { queued.countDown() })
        val executor = Executors.newSingleThreadExecutor()
        val running = executor.submit { server.run() }
        val request = ServeWire.encode(ServeRequest(server.metadata.token, listOf("info"), root.toString())) + "\n"
        val clients = mutableListOf<java.net.Socket>()
        try {
            send(server, clients, request)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            repeat(16) {
                send(server, clients, request)
            }
            assertTrue(queued.await(5, TimeUnit.SECONDS))
            server.connect().use { extra ->
                extra.soTimeout = 5_000
                assertEquals(-1, extra.getInputStream().read())
            }
            assertFallbacks(server)
        } finally {
            release.countDown()
            clients.forEach { it.close() }
            server.requestStop()
            running.get(10, TimeUnit.SECONDS)
            executor.shutdownNow()
            server.close()
        }
    }

    private fun assertFallbacks(server: ServeServer) {
        val root = server.workspace
        val refused = runKscope("scan", "--workspace", root.toString())
        assertEquals(1, refused.statusCode)
        assertTrue(refused.stderr.contains("busy or unreachable"))
        assertTrue(refused.stderr.contains("kscope serve --stop"))
        assertEquals(1, refused.stderr.trimEnd().lines().size)
        val local = runKscope("info", "--no-server", "--workspace", root.toString())
        assertEquals(local.stdout, runKscope("info", "--workspace", root.toString()).stdout)
    }

    private fun send(server: ServeServer, clients: MutableList<java.net.Socket>, request: String) {
        server.connect().also {
            clients.add(it)
            it.getOutputStream().write(request.toByteArray())
        }
    }
}
