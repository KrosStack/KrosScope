package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.writeFile
import io.github.krosstack.kscope.service.KrosScope
import kotlinx.serialization.json.jsonArray
import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeIndexedMapTest {
    private val withMap = ServeSettings(mapEnabled = true)

    private fun encode(text: String) = URLEncoder.encode(text, Charsets.UTF_8)

    @Test
    fun `the map of an indexed workspace lists its files and answers questions about them`() =
        inScannedWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))

                val map = address.get("api/map")
                val file = map.nodeId("Lib.kt")
                val node = address.get("api/node?id=${encode(file)}")
                val impact = address.get("api/impact?id=${encode(file)}")
                val task = address.get("api/task?text=Lib")

                assertEquals(200, map.status)
                listOf(map, node, impact, task).forEach { assertEquals("1", it.headers["x-map-version"]) }
                assertEquals(200, node.status)
                assertTrue(node.json().getValue("symbols").jsonArray.isNotEmpty(), node.body)
                assertEquals(200, impact.status)
                assertEquals(file, impact.json().text("requested"))
                assertEquals(200, task.status)
                assertTrue(task.json().getValue("files").jsonArray.isNotEmpty(), task.body)
            }
        }

    @Test
    fun `a question that names nothing or too much is refused and one that names nothing known is an error value`() =
        inScannedWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))

                assertEquals(404, address.get("api/node?id=nothing").status)
                assertEquals(404, address.get("api/node").status)
                assertEquals(400, address.get("api/impact").status)
                assertEquals(400, address.get("api/task").status)
                assertEquals(400, address.get("api/task?text=${"x".repeat(TOO_LONG)}").status)
                assertEquals(200, address.get("api/task?text=${"x".repeat(TOO_LONG - 1)}").status)
                val unknown = address.get("api/impact?symbol=nothing")
                assertEquals(200, unknown.status)
                assertTrue(unknown.json().containsKey("error"))
            }
        }

    @Test
    fun `an index that another process rebuilds is announced to a viewer with a new version`() =
        inScannedWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                address.events().use { stream ->
                    val first = stream.untilIndexed().version

                    writeFile(root, "lib/src/main/kotlin/Extra.kt", "class Extra\n")
                    KrosScope.open(root).use { it.scan() }

                    generateSequence { stream.nextState() }.first { it.version > first }
                    assertTrue(address.get("api/map").labels().contains("Extra.kt"))
                }
            }
        }

    private companion object {
        const val TOO_LONG = 2_001
    }
}
