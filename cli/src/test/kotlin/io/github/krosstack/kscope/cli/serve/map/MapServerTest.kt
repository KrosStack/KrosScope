package io.github.krosstack.kscope.cli.serve.map

import java.net.Socket
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapServerTest {
    private val assets = MapAssets { name ->
        when (name) {
            "MANIFEST" -> "index.html\nmain.js\n".toByteArray()
            "index.html" -> "<!doctype html>".toByteArray()
            "main.js" -> "export {};".toByteArray()
            else -> null
        }
    }

    private fun <T> serving(block: (MapServer, MapAddress, MapHost) -> T): T {
        val host = MapHost(Path.of("workspace"), null) { 0L }
        return MapServer.open("secret", host, assets).use { server ->
            server.start()
            block(server, MapAddress(server.url), host)
        }
    }

    @Test
    fun `the map listens on the loopback address only`() = serving { server, address, _ ->
        assertEquals("127.0.0.1", server.boundAddress.hostAddress)
        assertTrue(server.url.startsWith("http://127.0.0.1:"), server.url)
        assertTrue(server.url.endsWith("/secret/"), server.url)
        assertEquals("secret", address.token)
    }

    @Test
    fun `the page and its files are answered with their length`() = serving { _, address, _ ->
        val page = address.get("")
        val script = address.get("app/main.js")

        assertEquals(200, page.status)
        assertEquals("<!doctype html>", page.body)
        assertEquals("15", page.headers["content-length"])
        assertEquals("close", page.headers["connection"])
        assertEquals("no-store", page.headers["cache-control"])
        assertEquals("nosniff", page.headers["x-content-type-options"])
        assertEquals("no-referrer", page.headers["referrer-policy"])
        assertTrue(page.headers.getValue("content-security-policy").contains("script-src 'self'"))
        assertEquals("text/javascript; charset=utf-8", script.headers["content-type"])
    }

    @Test
    fun `a request with the wrong host or method changes nothing and is refused`() = serving { _, address, _ ->
        assertEquals(403, address.get("", host = "evil.example").status)
        assertEquals(405, address.get("", method = "POST").status)
        assertEquals(404, address.get("app/MANIFEST").status)
    }

    @Test
    fun `a viewer is told the state at once and the stream is event stream`() = serving { _, address, _ ->
        address.events().use { stream ->
            val state = stream.nextState()

            assertTrue(stream.head.contains("Content-Type: text/event-stream"), stream.head)
            assertTrue(stream.head.contains("Cache-Control: no-store"), stream.head)
            assertEquals(false, state?.get("indexed")?.toString()?.toBoolean())
        }
    }

    @Test
    fun `closing the server ends the stream of a viewer`() {
        val host = MapHost(Path.of("workspace"), null) { 0L }
        val server = MapServer.open("secret", host, assets)
        server.start()
        MapAddress(server.url).events().use { stream ->
            stream.nextState()

            server.close()

            assertEquals("", stream.readToEnd().trim())
        }
    }

    @Test
    fun `connections beyond the limit are closed and the others are kept`() = serving { _, address, _ ->
        val viewers = List(MapServer.HANDLERS) { address.events().also { viewer -> viewer.nextState() } }
        try {
            Socket("127.0.0.1", address.port).use { extra ->
                extra.soTimeout = MapAddress.READ_TIMEOUT_MILLIS
                val answered = runCatching { extra.getInputStream().read() }.getOrDefault(-1)

                assertEquals(-1, answered)
            }
        } finally {
            viewers.forEach(EventStream::close)
        }
    }
}
