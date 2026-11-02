package io.github.krosstack.kscope.cli.service.page

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusPageRouterTest {
    private var renders = 0
    private val router = StatusPageRouter("tok", 4000) {
        renders++
        "page"
    }
    private val good = StatusPageRequest("GET", "/tok/", "127.0.0.1:4000")

    @Test
    fun `a get of the page path from the loopback host is answered with the page`() {
        val response = router.answer(good)

        assertEquals(200, response.status)
        assertEquals("page", response.body)
    }

    @Test
    fun `localhost and a query string are accepted`() {
        assertEquals(200, router.answer(good.copy(host = "localhost:4000", target = "/tok/?x=1")).status)
    }

    @Test
    fun `every method but get is refused without rendering`() {
        for (method in listOf("POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE", "CONNECT", "get")) {
            val response = router.answer(good.copy(method = method))

            assertEquals(405, response.status, method)
            assertEquals("GET", response.headers["Allow"], method)
        }
        assertEquals(0, renders)
    }

    @Test
    fun `a wrong or missing token is not found, and so is every other path`() {
        for (target in listOf("/", "/tok", "/other/", "/tok/x", "/TOK/", "/tok//", "/status")) {
            assertEquals(404, router.answer(good.copy(target = target)).status, target)
        }
        assertEquals(0, renders)
    }

    @Test
    fun `another host name is refused, so that a rebound name cannot read the page`() {
        for (host in listOf("evil.example:4000", "127.0.0.1", "127.0.0.1:4001", "[::1]:4000", "")) {
            assertEquals(403, router.answer(good.copy(host = host)).status, host)
        }
        assertEquals(403, router.answer(good.copy(host = null)).status)
        assertEquals(0, renders)
    }

    @Test
    fun `an unreadable request is a bad request`() {
        assertEquals(400, router.answer(null).status)
    }

    @Test
    fun `every response forbids caching, framing and scripts`() {
        val bytes = String(router.answer(good).toBytes(), Charsets.ISO_8859_1)

        assertTrue(bytes.startsWith("HTTP/1.1 200 OK\r\n"), bytes)
        assertTrue(bytes.contains("Cache-Control: no-store\r\n"), bytes)
        assertTrue(bytes.contains("Content-Security-Policy: default-src 'none'"), bytes)
        assertTrue(bytes.contains("Content-Length: 4\r\n"), bytes)
        assertTrue(bytes.endsWith("\r\n\r\npage"), bytes)
    }

    @Test
    fun `a request head is read to its end and its host header is found in any letter case`() {
        val head = "GET /tok/ HTTP/1.1\r\nhOsT:  127.0.0.1:4000 \r\nAccept: */*\r\n\r\n"

        val request = StatusPageRequest.read(head.byteInputStream(Charsets.ISO_8859_1))

        assertNotNull(request)
        assertEquals(StatusPageRequest("GET", "/tok/", "127.0.0.1:4000"), request)
        assertNull(StatusPageRequest.read("GET /tok/\r\n\r\n".byteInputStream()))
        assertNull(StatusPageRequest.read("GET /tok/ HTTP/1.1\r\n".byteInputStream()))
    }
}
