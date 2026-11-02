package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.ServeAnswer
import io.github.krosstack.kscope.cli.serve.ServeSettings
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.service.inServiceHome
import io.github.krosstack.kscope.cli.service.inTwoScannedWorkspaces
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.ScanResult
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceScanTest {
    private val result = ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "")

    private fun awaitScanWaitingForItsTurn() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        val queued = {
            Thread.getAllStackTraces().any { (thread, frames) ->
                thread.state == Thread.State.WAITING && frames.any { it.className.contains("ReentrantLock") }
            }
        }
        while (!queued()) {
            check(System.nanoTime() < deadline) { "no scan ever waited for its turn" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `scans requested together in two workspaces never overlap`() = inTwoScannedWorkspaces { first, second ->
        inServiceHome { home ->
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val running = AtomicInteger()
            val maximum = AtomicInteger()
            val scans = AtomicInteger()
            val scan = ScanRunner { _, _, _, _ ->
                maximum.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                scans.incrementAndGet()
                entered.countDown()
                check(release.await(WAIT_SECONDS, TimeUnit.SECONDS))
                running.decrementAndGet()
                result
            }
            val requests = CountDownLatch(2)
            val serve = ServeSettings(onRequest = { requests.countDown() }, watch = quietWatch())
            withTestService(home, listOf(first, second), ServiceSettings(serve, scan)) { service ->
                val servers = listOf(first, second).map { service.awaitServed(it) }
                val executor = Executors.newFixedThreadPool(2)
                try {
                    val answers = servers.map { server ->
                        executor.submit<ServeAnswer> { server.query(listOf("scan")) }
                    }
                    assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    assertTrue(requests.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    awaitScanWaitingForItsTurn()
                    assertEquals(1, running.get())

                    release.countDown()

                    answers.forEach { assertEquals(0, it.get(WAIT_SECONDS, TimeUnit.SECONDS).exitCode) }
                    assertEquals(1, maximum.get())
                    assertEquals(2, scans.get())
                } finally {
                    release.countDown()
                    executor.shutdownNow()
                }
            }
        }
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
