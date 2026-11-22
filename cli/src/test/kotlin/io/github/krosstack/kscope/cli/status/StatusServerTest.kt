package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StatusServerTest {
    @Test
    fun `a server that runs is reported without being asked`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val result = runStatus(root)

            val running = "; server running (pid ${ProcessHandle.current().pid()})"
            assertTrue(result.stdout.trimEnd().endsWith(running), result.stdout)
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `a file left by a server that is gone is a stale file, and a server of an older version is not known`() =
        inScannedWorkspace { root ->
            val pid = ProcessHandle.current().pid()
            val reused = ServeMetadata(pid, 1, "secret", "1.0", 1, "then", Instant.EPOCH.toString())
            val older = ServeMetadata(pid, 1, "secret", "1.0", 1, "then")

            reused.write(root)
            val stale = runStatus(root, "--format", "json")
            older.write(root)
            val unknown = runStatus(root)

            val server = parseJson(stale.stdout).getValue("server").jsonObject
            assertEquals("notRunning", server.getValue("state").jsonPrimitive.content)
            assertTrue(server.getValue("stale").jsonPrimitive.boolean)
            val notKnown = "; server unknown (pid $pid is alive, but it cannot be checked)"
            assertTrue(unknown.stdout.trimEnd().endsWith(notKnown), unknown.stdout)
            assertFalse(unknown.stdout.contains("running"))
            assertEquals(0, unknown.statusCode)
        }
}
