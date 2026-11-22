package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.diagram.inDiagramWorkspace
import io.github.krosstack.kscope.cli.diagram.runDiagram
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeDiagramMapTest {
    private val withMap = ServeSettings(mapEnabled = true)

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

    private fun symbolId(address: MapAddress, file: String, name: String): String =
        address.get("api/node?id=${encode(file)}").json().getValue("symbols").jsonArray.map { it.jsonObject }
            .first { it.text("name") == name }.text("id")

    private fun selectedNode(answer: JsonObject): JsonObject =
        answer.getValue("diagram").jsonObject.getValue("nodes").jsonArray.map { it.jsonObject }
            .single { it["selected"]?.jsonPrimitive?.boolean == true }

    @Test
    fun `a function of the map is answered with the diagram that the diagram command draws`() {
        inDiagramWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                val file = address.get("api/map").nodeId("Main.kt")
                val run = symbolId(address, file, "run")

                val answer = address.get("api/diagram?node=${encode(run)}&depth=2&links=60")

                val command = runDiagram(root, "symbol", run, "--depth", "2", "--links", "60", "--format", "json")
                val expected = parseJson(command.stdout).filterKeys { it != "schemaVersion" && it != "indexLevel" }
                assertEquals(200, answer.status)
                assertEquals("1", answer.headers["x-map-version"])
                assertEquals(JsonObject(expected), answer.json().getValue("diagram").jsonObject)
            }
        }
    }

    @Test
    fun `the selected node of the diagram carries its symbol id, and the answer names its file in the map`() {
        inDiagramWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                val file = address.get("api/map").nodeId("Main.kt")
                val run = symbolId(address, file, "run")

                val answer = address.get("api/diagram?node=${encode(run)}").json()

                assertEquals(run, selectedNode(answer).text("symbolId"))
                assertEquals(file, answer.getValue("files").jsonObject.getValue(run).jsonPrimitive.content)
            }
        }
    }

    @Test
    fun `a type of the map is drawn and all depths are asked with all`() {
        inDiagramWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                val file = address.get("api/map").nodeId("Greeter.kt")
                val greeter = symbolId(address, file, "Greeter")

                val answer = address.get("api/diagram?node=${encode(greeter)}&depth=all")

                assertEquals(200, answer.status)
                assertEquals("hello.Greeter", selectedNode(answer.json()).text("label"))
            }
        }
    }

    @Test
    fun `a symbol that a diagram does not draw and a name that is no id are answered with an error value`() {
        inDiagramWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                val file = address.get("api/map").nodeId("Greeter.kt")
                val total = symbolId(address, file, "total")

                val property = address.get("api/diagram?node=${encode(total)}")
                val byName = address.get("api/diagram?node=Greeter")

                assertEquals(200, property.status)
                assertTrue(property.json().text("error").contains("property"), property.body)
                assertEquals(200, byName.status)
                assertEquals("The map has no such symbol.", byName.json().text("error"))
                assertFalse(byName.json().containsKey("diagram"))
            }
        }
    }

    @Test
    fun `a workspace without an index answers a diagram question with an error value`() {
        inWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))

                val answer = address.get("api/diagram?node=anything")

                assertEquals(200, answer.status)
                assertEquals("The map has no such symbol.", answer.json().text("error"))
            }
        }
    }
}
