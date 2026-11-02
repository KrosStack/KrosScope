package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.runKscope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeBusyTest {
    @Test
    fun `a scan with a mismatched token is refused beside a live server`() = inScannedWorkspace { root ->
        withTestServer(root) { server ->
            val metadata = assertNotNull(ServeMetadata.read(root))
            metadata.copy(token = "wrong").write(root)
            val result = runKscope("scan", "--workspace", root.toString())
            assertEquals(1, result.statusCode)
            assertTrue(result.stderr.contains("busy or unreachable"))
            assertTrue(result.stderr.contains("kscope serve --stop"))
            assertEquals(1, result.stderr.trimEnd().lines().size)
            assertEquals(0, server.requestCount)
            assertEquals(0, runKscope("info", "--workspace", root.toString()).statusCode)
        }
    }

    @Test
    fun `an opted out scan is refused while the server is busy`() = inScannedWorkspace { root ->
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
                val result = runKscope("scan", "--no-server", "--workspace", root.toString())
                assertEquals(1, result.statusCode)
                assertTrue(result.stderr.contains("stopped first"))
                assertEquals(1, server.requestCount)
            } finally {
                release.countDown()
                assertEquals(0, running.get(30, TimeUnit.SECONDS).exitCode)
                executor.shutdownNow()
            }
        }
    }
}
