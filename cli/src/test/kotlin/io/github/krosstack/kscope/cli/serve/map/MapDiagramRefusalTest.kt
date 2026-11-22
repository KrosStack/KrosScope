package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.service.page.StatusPageRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MapDiagramRefusalTest {
    private val host = MapHost(Path.of("workspace"), null) { 0L }
    private val router = MapRouter("secret", PORT, MapAssets { null }, MapApi(host))

    private fun get(target: String, method: String = "GET") =
        router.answer(StatusPageRequest(method, target, "127.0.0.1:$PORT")) as MapResponse.Plain

    private fun message(answer: MapResponse.Plain): String =
        Json.parseToJsonElement(answer.body.toString(Charsets.UTF_8)).jsonObject.getValue("error").jsonPrimitive.content

    @Test
    fun `a diagram question without a node is a bad request with a sentence for the page`() {
        val answer = get("/secret/api/diagram")

        assertEquals(400, answer.status)
        assertEquals("Name a symbol with node.", message(answer))
    }

    @Test
    fun `a depth or a number of lines that is out of range is a bad request`() {
        val targets = listOf(
            "/secret/api/diagram?node=s&depth=0",
            "/secret/api/diagram?node=s&depth=51",
            "/secret/api/diagram?node=s&depth=deep",
            "/secret/api/diagram?node=s&links=1001",
            "/secret/api/diagram?node=s&links=-1",
        )

        targets.forEach { assertEquals(400, get(it).status, it) }
    }

    @Test
    fun `a diagram question needs the token and a get like every other`() {
        assertEquals(404, get("/other/api/diagram?node=s").status)
        assertEquals(405, get("/secret/api/diagram?node=s", method = "POST").status)
        assertEquals(400, get("/secret/api/diagram?node=%zz").status)
    }

    private companion object {
        const val PORT = 4242
    }
}
