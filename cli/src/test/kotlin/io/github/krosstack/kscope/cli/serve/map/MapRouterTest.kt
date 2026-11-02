package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.service.page.StatusPageRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapRouterTest {
    private val files = mapOf(
        "MANIFEST" to "index.html\napp.js\nstyle.css\nicon.svg\nnotes.txt\nmissing.js\n../secret.txt\nsub/inner.js\n",
        "index.html" to "<!doctype html>",
        "app.js" to "export {};",
        "style.css" to "body {}",
        "icon.svg" to "<svg/>",
        "notes.txt" to "notes",
        "secret.txt" to "secret",
        "../secret.txt" to "secret",
        "sub/inner.js" to "inner",
    )
    private val host = MapHost(Path.of("workspace"), null) { 0L }
    private val router = MapRouter("secret", PORT, MapAssets { name -> files[name]?.toByteArray() }, MapApi(host))

    private fun get(target: String, hostHeader: String? = "127.0.0.1:$PORT", method: String = "GET") =
        router.answer(StatusPageRequest(method, target, hostHeader)) as MapResponse.Plain

    @Test
    fun `the address with the token is answered with the page`() {
        val answer = get("/secret/")

        assertEquals(200, answer.status)
        assertEquals("text/html; charset=utf-8", answer.contentType)
        assertEquals("<!doctype html>", answer.body.toString(Charsets.UTF_8))
    }

    @Test
    fun `a listed file is answered with the type of its suffix`() {
        assertEquals("text/javascript; charset=utf-8", get("/secret/app/app.js").contentType)
        assertEquals("text/css; charset=utf-8", get("/secret/app/style.css").contentType)
        assertEquals("image/svg+xml", get("/secret/app/icon.svg").contentType)
        assertEquals("application/octet-stream", get("/secret/app/notes.txt").contentType)
    }

    @Test
    fun `a file that the manifest does not list is not found even when it exists`() {
        assertEquals(404, get("/secret/app/secret.txt").status)
        assertEquals(404, get("/secret/app/MANIFEST").status)
    }

    @Test
    fun `a file that the manifest lists and the resources lack is not found`() {
        assertEquals(404, get("/secret/app/missing.js").status)
    }

    @Test
    fun `a name that leaves the directory is not found whatever the manifest says`() {
        val names = listOf("../secret.txt", "..%2Fsecret.txt", "sub/inner.js", "%2e%2e/secret.txt", "..\\secret.txt")
        for (name in names) {
            assertEquals(404, get("/secret/app/$name").status, name)
        }
        assertEquals(404, get("/secret/app/../app/app.js").status)
    }

    @Test
    fun `a request with the wrong token or without the final slash is not found`() {
        assertEquals(404, get("/other/").status)
        assertEquals(404, get("/secret").status)
        assertEquals(404, get("/").status)
        assertEquals(404, get("/secretx/").status)
        assertEquals(404, get("/secret/unknown").status)
        assertEquals(404, get("/secret/api/unknown").status)
    }

    @Test
    fun `a request with another method than get is refused with the allowed one`() {
        for (method in listOf("POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS")) {
            val answer = get("/secret/", method = method)

            assertEquals(405, answer.status, method)
            assertEquals("GET", answer.headers["Allow"], method)
        }
    }

    @Test
    fun `a request for another host is forbidden and localhost is allowed`() {
        assertEquals(403, get("/secret/", hostHeader = "evil.example:$PORT").status)
        assertEquals(403, get("/secret/", hostHeader = "127.0.0.1:${PORT + 1}").status)
        assertEquals(403, get("/secret/", hostHeader = null).status)
        assertEquals(200, get("/secret/", hostHeader = "localhost:$PORT").status)
    }

    @Test
    fun `a request that cannot be read is a bad request`() {
        assertEquals(400, (router.answer(null) as MapResponse.Plain).status)
        assertEquals(400, get("/secret/api/node?id=%zz").status)
    }

    @Test
    fun `every answer forbids caching sniffing referrers and any source but its own`() {
        val answer = get("/secret/")
        val policy = answer.toBytes().toString(Charsets.ISO_8859_1)

        assertTrue(policy.contains("Cache-Control: no-store\r\n"), policy)
        assertTrue(policy.contains("X-Content-Type-Options: nosniff\r\n"), policy)
        assertTrue(policy.contains("Referrer-Policy: no-referrer\r\n"), policy)
        assertTrue(policy.contains("Connection: close\r\n"), policy)
        assertTrue(policy.contains("Content-Length: 15\r\n"), policy)
        assertTrue(policy.contains("default-src 'none'; script-src 'self'; style-src 'self';"), policy)
        assertTrue(policy.contains("connect-src 'self'; img-src 'self' data:;"), policy)
        assertTrue(policy.contains("frame-ancestors 'none'; base-uri 'none'; form-action 'none'"), policy)
    }

    private companion object {
        const val PORT = 4242
    }
}
