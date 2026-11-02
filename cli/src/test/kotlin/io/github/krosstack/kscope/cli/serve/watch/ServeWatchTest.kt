package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeWatchTest {
    @Test
    fun `without watch the injected stamp source is never read`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val settings = inputs.settings().copy(watchEnabled = false)
        withTestServer(root, settings) { server ->
            assertEquals(0, server.query(listOf("info")).exitCode)
            assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
            assertEquals(0, inputs.reads.get())
            assertEquals(null, server.watchState)
        }
    }

    @Test
    fun `automatic scans reuse the runner without trust options or progress`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val scanned = CountDownLatch(1)
        val scan = ScanRunner { workspace, options, rebuild, verify ->
            assertFalse(options.trustBuild)
            assertFalse(rebuild)
            assertFalse(verify)
            KrosScope.open(workspace, options).use { it.scan() }.also { scanned.countDown() }
        }
        withTestServer(root, inputs.settings(), scan) { server ->
            inputs.awaitFirstPoll()
            inputs.poll(server, 4, "initial")
            assertTrue(scanned.await(10, TimeUnit.SECONDS))
            assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
            assertEquals(1, server.requestCount)
            assertEquals(null, assertNotNull(server.watchState).reason())
        }
    }

    @Test
    fun `requested scans record a new stamp and resume watching without executing Gradle`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            var trusted = false
            val scan = ScanRunner { workspace, options, rebuild, verify ->
                trusted = options.trustBuild
                KrosScope.open(workspace, options.copy(trustBuild = false)).use { it.scan(rebuild, verify) }
            }
            withTestServer(root, inputs.settings(), scan) { server ->
                inputs.awaitFirstPoll()
                assertEquals(0, server.query(listOf("scan", "--trust-build", "--quiet")).exitCode)
                assertTrue(trusted)
                inputs.poll(server, 1)
                inputs.trusted.set(true)
                assertNotNull(server.watchState).setTrusted(true)
                inputs.poll(server, 5)
                assertFalse(assertNotNull(server.watchState).beginUpdate())
                inputs.trusted.set(false)
                assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
                assertFalse(trusted)
                assertEquals(null, assertNotNull(server.watchState).reason())
                assertTrue(inputs.reads.get() >= 5)
            }
        }
}
