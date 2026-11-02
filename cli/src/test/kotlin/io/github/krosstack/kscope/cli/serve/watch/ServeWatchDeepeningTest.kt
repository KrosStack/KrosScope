package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ServeWatchDeepeningTest {
    private fun countingRunner(scans: AtomicInteger, onScan: (Int) -> Unit = {}) =
        ScanRunner { workspace, options, rebuild, verify ->
            KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }.also { onScan(scans.incrementAndGet()) }
        }

    @Test
    fun `an idle workspace is deepened by itself once its depth is wanted, and not before`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            val scans = AtomicInteger()
            val deepened = CountDownLatch(1)
            val runner = countingRunner(scans) { if (it == 2) deepened.countDown() }
            withTestServer(root, inputs.settings(), runner) { server ->
                inputs.awaitFirstPoll()
                assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
                inputs.poll(server, 0, "initial")
                inputs.poll(server, 10, "initial")
                assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
                assertEquals(1, scans.get())

                inputs.shallow.set(true)
                inputs.poll(server, 12, "initial")

                assertTrue(deepened.await(10, TimeUnit.SECONDS))
            }
        }

    @Test
    fun `a trust that is revoked after the look is honoured before the update runs`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val scans = AtomicInteger()
        withTestServer(root, inputs.settings(), countingRunner(scans)) { server ->
            inputs.awaitFirstPoll()
            assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
            inputs.poll(server, 0, "initial")
            inputs.poll(server, 10, "initial")
            assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)

            inputs.shallowAnswers.addAll(listOf(true, false))
            inputs.poll(server, 12, "initial")
            assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)

            assertTrue(inputs.shallowAnswers.isEmpty())
            assertEquals(1, scans.get())
            assertFalse(assertNotNull(server.watchState).shallow)
        }
    }

    @Test
    fun `a scan that succeeds but leaves the index shallow is not repeated until the backoff has passed`() =
        inScannedWorkspace { root ->
            val inputs = WatchServerInputs()
            val scans = AtomicInteger()
            withTestServer(root, inputs.settings(), countingRunner(scans)) { server ->
                inputs.awaitFirstPoll()
                inputs.shallow.set(true)
                assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
                inputs.leaveShallow(server, 1)
                assertEquals(2, scans.get())

                inputs.poll(server, 8, "initial")
                inputs.poll(server, 12, "initial")
                assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
                assertEquals(2, scans.get())

                inputs.poll(server, 16, "initial")
                assertEquals(0, server.query(listOf("info", "--quiet")).exitCode)
                assertEquals(3, scans.get())
            }
        }
}
