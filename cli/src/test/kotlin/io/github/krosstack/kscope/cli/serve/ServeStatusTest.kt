package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeStatusTest {
    private val documented = setOf("pid", "port", "token", "toolVersion", "protocol", "startTime")

    private fun stored(root: Path): JsonObject =
        ServeWire.json.parseToJsonElement(Files.readString(ServeMetadata.file(root))).jsonObject

    @Test
    fun `serve json holds the same fields whether the server watches or not`() = inScannedWorkspace { root ->
        withTestServer(root) { assertEquals(documented, stored(root).keys) }
        withTestServer(root, ServeSettings(watchEnabled = true)) { assertEquals(documented, stored(root).keys) }
    }

    @Test
    fun `serve json with a field of a later version is still read`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val later = JsonObject(stored(root) + ("somethingNew" to JsonPrimitive(true)))
            Files.writeString(ServeMetadata.file(root), later.toString())

            val read = assertNotNull(ServeMetadata.read(root))

            assertEquals(server.metadata.port, read.port)
            assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun `status does not wait for a server that is busy`() = inScannedWorkspace { root ->
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val settings = ServeSettings(onRequest = {
            entered.countDown()
            check(release.await(30, TimeUnit.SECONDS))
        })
        withTestServer(root, settings) { server ->
            val executor = Executors.newSingleThreadExecutor()
            val running = executor.submit<ServeAnswer> { server.query(listOf("info")) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                val started = System.nanoTime()

                val status = runKscope("serve", "--status", "--workspace", root.toString())

                val seconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
                assertEquals(0, status.statusCode, status.output)
                assertTrue(status.stdout.contains("Server runs for"), status.output)
                assertTrue(status.stdout.contains("did not answer within 2 seconds"), status.output)
                assertTrue(seconds < 15, "status took $seconds s")
            } finally {
                release.countDown()
                assertEquals(0, running.get(30, TimeUnit.SECONDS).exitCode)
                executor.shutdownNow()
            }
        }
    }
}
