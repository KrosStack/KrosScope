package io.github.krosstack.kscope.analyzer.schema.openapi

import kotlin.test.Test
import kotlin.test.assertEquals

private fun serversOf(vararg urls: String): List<String> =
    documentOf("openapi: 3.0.3\nservers:\n" + urls.joinToString("") { "  - url: \"$it\"\n" }).servers

class OpenApiServersTest {
    @Test
    fun `a relative server is kept by its path without the trailing slash`() {
        assertEquals(listOf("/api/v2"), serversOf("/api/v2/"))
    }

    @Test
    fun `an absolute server is kept by the path of its URL`() {
        assertEquals(listOf("/v1"), serversOf("https://api.example.com/v1/"))
        assertEquals(listOf("/a/b"), serversOf("http://localhost:8080/a/b"))
        assertEquals(listOf("/v1/{basePath}"), serversOf("https://{host}.example.com/v1/{basePath}"))
    }

    @Test
    fun `the query and the fragment of a server are not part of its path`() {
        assertEquals(listOf("/v1"), serversOf("https://api.example.com/v1?key=1#top"))
        assertEquals(listOf("/v1"), serversOf("/v1?key=1"))
        assertEquals(listOf("/v1"), serversOf("https://api.example.com/v1#top"))
    }

    @Test
    fun `a server with no path, or with the root as its path, is not kept`() {
        assertEquals(emptyList(), serversOf("https://api.example.com", "https://api.example.com/", "/", "//", ""))
        assertEquals(emptyList(), serversOf("https://api.example.com?x=1", "http://host:80#top"))
    }

    @Test
    fun `a URL that is no URL of this kind is not kept`() {
        assertEquals(emptyList(), serversOf("//host/v1", "api.example.com/v1", "v1/", "mailto:x@example.com"))
    }

    @Test
    fun `servers that give one path are kept once, in the order of the file`() {
        assertEquals(
            listOf("/v2", "/v1"),
            serversOf("https://a.example.com/v2", "/v2", "https://b.example.com/v1/", "/v1"),
        )
    }

    @Test
    fun `the base path of a Swagger 2 document is a server`() {
        assertEquals(listOf("/v1"), documentOf("swagger: \"2.0\"\nbasePath: /v1/\n").servers)
    }
}
