package io.github.krosstack.kscope.cli.serve.watch

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.serve.ServeAnswer
import io.github.krosstack.kscope.cli.serve.ServeMetadata
import io.github.krosstack.kscope.cli.serve.ServeServer
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServeWatchOrderingTest {
    @Test
    fun `a wanted update waits until a blocked request finishes`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scans = AtomicInteger()
        val scanned = CountDownLatch(1)
        val scan = ScanRunner { workspace, options, _, _ ->
            scans.incrementAndGet()
            KrosScope.open(workspace, options).use { it.scan() }.also { scanned.countDown() }
        }
        val settings = inputs.settings().copy(onRequest = {
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
        })
        withTestServer(root, settings, scan) { server ->
            inputs.awaitFirstPoll()
            val executor = Executors.newSingleThreadExecutor()
            val query = executor.submit<ServeAnswer> { server.query(listOf("info")) }
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                inputs.poll(server, 4, "initial")
                assertEquals(0, scans.get())
                release.countDown()
                assertEquals(0, query.get(10, TimeUnit.SECONDS).exitCode)
                assertTrue(scanned.await(10, TimeUnit.SECONDS))
                assertEquals(1, scans.get())
            } finally {
                release.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `a request arriving during a blocked update is answered afterwards`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queued = CountDownLatch(1)
        val requested = AtomicInteger()
        val scan = ScanRunner { workspace, options, _, _ ->
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            KrosScope.open(workspace, options).use { it.scan() }
        }
        val settings = inputs.settings().copy(onRequest = { requested.incrementAndGet() })
        withTestServer(root, settings, scan, onQueued = { queued.countDown() }) { server ->
            inputs.awaitFirstPoll()
            inputs.poll(server, 4, "initial")
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val executor = Executors.newSingleThreadExecutor()
            val query = executor.submit<ServeAnswer> { server.query(listOf("info")) }
            try {
                assertTrue(queued.await(5, TimeUnit.SECONDS))
                assertFalse(query.isDone)
                assertEquals(0, requested.get())
                release.countDown()
                assertEquals(0, query.get(10, TimeUnit.SECONDS).exitCode)
                assertEquals(1, requested.get())
            } finally {
                release.countDown()
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `automatic updates do not reset idle expiry`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        val scan = ScanRunner { workspace, options, _, _ ->
            KrosScope.open(workspace, options).use { it.scan() }.also {
                inputs.clock.set(TimeUnit.SECONDS.toNanos(12))
            }
        }
        val server = ServeServer.open(root, inputs.settings(idleMillis = 10_000), scan)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val running = executor.submit { server.run() }
            inputs.awaitFirstPoll()
            inputs.poll(server, 4, "initial")
            running.get(10, TimeUnit.SECONDS)
            assertEquals(0, server.requestCount)
            assertFalse(Files.exists(ServeMetadata.file(root)))
        } finally {
            server.requestStop()
            executor.shutdownNow()
            server.close()
        }
    }
}
