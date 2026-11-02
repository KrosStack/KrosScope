package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeAnswer
import io.github.krosstack.kscope.cli.serve.ServeClient
import io.github.krosstack.kscope.cli.serve.ServeRequest
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServeWatchControlTest {
    @Test
    fun `a waiting status is answered before a wanted automatic update`() =
        controlsWhileBlocked(listOf("status", "stop"))

    @Test
    fun `a waiting stop is answered before a wanted automatic update`() = controlsWhileBlocked(listOf("stop"))

    private fun controlsWhileBlocked(actions: List<String>) = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queued = LinkedBlockingQueue<Unit>()
        val scans = AtomicInteger()
        val settings = blockedSettings(inputs, entered, release)
        val scan = countingRunner(scans)
        withTestServer(root, settings, scan, onQueued = { queued.add(Unit) }) { server ->
            inputs.awaitFirstPoll()
            val executor = Executors.newFixedThreadPool(3)
            try {
                val query = executor.submit<ServeAnswer> { server.query(listOf("info")) }
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertEquals(Unit, queued.poll(5, TimeUnit.SECONDS))
                inputs.poll(server, 4, "initial")
                assertTrue(checkNotNull(server.watchState).updateWanted)
                val controls = actions.map { action ->
                    executor.submit<Int?> {
                        ServeClient.exchange(server.metadata, ServeRequest(server.metadata.token, action = action)) {}
                    }.also { assertEquals(Unit, queued.poll(5, TimeUnit.SECONDS)) }
                }
                assertEquals(0, scans.get())
                release.countDown()
                assertEquals(0, query.get(10, TimeUnit.SECONDS).exitCode)
                controls.forEach { assertEquals(0, it.get(10, TimeUnit.SECONDS)) }
                assertEquals(0, scans.get())
            } finally {
                release.countDown()
                executor.shutdownNow()
            }
        }
    }

    private fun blockedSettings(
        inputs: WatchServerInputs,
        entered: CountDownLatch,
        release: CountDownLatch,
    ): ServeSettings = inputs.settings().copy(onRequest = {
        entered.countDown()
        check(release.await(10, TimeUnit.SECONDS))
    })

    private fun countingRunner(scans: AtomicInteger): ScanRunner = ScanRunner { workspace, options, _, _ ->
        scans.incrementAndGet()
        KrosScope.open(workspace, options).use { it.scan() }
    }
}
