package io.github.krosstack.kscope.cli.service.page

import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusPageServerTest {
    private val renders = AtomicInteger()

    private fun <T> serving(block: (StatusPageServer) -> T): T =
        StatusPageServer.open("secret") { renders.incrementAndGet().toString() }.use { server ->
            server.start()
            block(server)
        }

    private fun port(server: StatusPageServer): Int =
        server.url.substringAfter("127.0.0.1:").substringBefore('/').toInt()

    private fun exchange(server: StatusPageServer, request: String): String =
        Socket("127.0.0.1", port(server)).use { client ->
            client.getOutputStream().apply {
                write(request.toByteArray(Charsets.ISO_8859_1))
                flush()
            }
            client.getInputStream().readAllBytes().toString(Charsets.UTF_8)
        }

    private fun request(method: String, path: String, server: StatusPageServer) =
        "$method $path HTTP/1.1\r\nHost: 127.0.0.1:${port(server)}\r\n\r\n"

    @Test
    fun `the page listens on the loopback address only`() = serving { server ->
        assertTrue(server.boundAddress.isLoopbackAddress, server.boundAddress.toString())
        assertEquals("127.0.0.1", server.boundAddress.hostAddress)
        assertTrue(server.url.startsWith("http://127.0.0.1:"), server.url)
        assertTrue(server.url.endsWith("/secret/"), server.url)
    }

    @Test
    fun `a get of the address is answered with the page`() = serving { server ->
        val answer = exchange(server, request("GET", "/secret/", server))

        assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"), answer)
        assertTrue(answer.endsWith("\r\n\r\n1"), answer)
    }

    @Test
    fun `a request with another method changes and renders nothing`() = serving { server ->
        for (method in listOf("POST", "PUT", "DELETE", "PATCH")) {
            val answer = exchange(server, request(method, "/secret/", server))

            assertTrue(answer.startsWith("HTTP/1.1 405 Method Not Allowed\r\n"), answer)
            assertTrue(answer.contains("Allow: GET\r\n"), answer)
        }
        assertEquals(0, renders.get())
    }

    @Test
    fun `a wrong token is not found and an oversized head is dropped, both without a page`() = serving { server ->
        val wrong = exchange(server, request("GET", "/other/", server))
        val head = "GET /" + "a".repeat(StatusPageRequest.MAX_HEAD_BYTES) + " HTTP/1.1\r\n"
        val oversized = runCatching { exchange(server, head) }.getOrDefault("")

        assertTrue(wrong.startsWith("HTTP/1.1 404 Not Found\r\n"), wrong)
        assertEquals("", oversized)
        assertEquals(0, renders.get())
    }

    @Test
    fun `closing the page stops the listener`() {
        val port = serving { server -> port(server) }

        val refused = runCatching { Socket("127.0.0.1", port).close() }

        assertTrue(refused.isFailure)
    }
}
