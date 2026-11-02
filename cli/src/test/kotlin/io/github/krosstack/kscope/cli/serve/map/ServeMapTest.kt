package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServeMapTest {
    private val withMap = ServeSettings(mapEnabled = true)

    @Test
    fun `serving without the flag opens no map`() = inWorkspace { root ->
        withTestServer(root) { server ->
            assertNull(server.mapUrl)
            assertFalse(runKscope("serve", "--status", "--workspace", root.toString()).stdout.contains("Map:"))
        }
    }

    @Test
    fun `status says where the map is`() = inWorkspace { root ->
        withTestServer(root, withMap) { server ->
            val status = runKscope("serve", "--status", "--workspace", root.toString())

            assertTrue(status.stdout.contains("Map: ${server.mapUrl}"), status.stdout)
            assertTrue(server.mapUrl.orEmpty().startsWith("http://127.0.0.1:"))
        }
    }

    @Test
    fun `a workspace without an index has an empty map and the answers to a question are errors`() =
        inWorkspace { root ->
            withTestServer(root, withMap) { server ->
                val address = MapAddress(checkNotNull(server.mapUrl))
                val before = server.query(listOf("info"))

                val map = address.get("api/map")
                val impact = address.get("api/impact?id=f%3Arepo%2F%2FLib.kt")
                val task = address.get("api/task?text=greet")

                assertEquals(200, map.status)
                assertEquals(0, map.json().count("nodes"))
                assertEquals(root.fileName.toString(), map.json().text("workspace"))
                assertEquals(200, impact.status)
                assertNotNull(impact.json()["error"])
                assertEquals(409, task.status)
                assertTrue(task.json().text("error").contains("no index"), task.body)
                assertEquals(200, address.get("api/map").status)
                assertEquals(before, server.query(listOf("info")))
            }
        }

    @Test
    fun `the first event of a viewer says that nothing is indexed and closing the server ends the stream`() =
        inWorkspace { root ->
            withTestServer(root, withMap) { server ->
                MapAddress(checkNotNull(server.mapUrl)).events().use { stream ->
                    val state = checkNotNull(stream.nextState())

                    assertFalse(state.flag("indexed"))
                    assertFalse(state.flag("scanning"))
                    assertFalse(state.flag("watching"))

                    server.requestStop()

                    assertTrue(generateSequence { stream.nextState() }.count() >= 0)
                    assertNull(stream.nextState())
                }
            }
        }
}
