package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SingleScanRunnerTest {
    private val result = ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), "")

    private fun scanIn(runner: ScanRunner, name: String) = runner.run(Path.of(name), OpenOptions(), false, false)

    private fun awaitWaiting(thread: Thread) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
        while (thread.state != Thread.State.WAITING) {
            check(System.nanoTime() < deadline) { "${thread.name} never waited; it is ${thread.state}" }
            Thread.onSpinWait()
        }
    }

    @Test
    fun `a scan requested while another runs waits for it to end`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val running = AtomicInteger()
        val overlaps = AtomicInteger()
        val order = mutableListOf<String>()
        val runner = SingleScanRunner(
            ScanRunner { workspace, _, _, _ ->
                if (running.incrementAndGet() > 1) overlaps.incrementAndGet()
                synchronized(order) { order += "start ${workspace.fileName}" }
                entered.countDown()
                if (workspace.fileName.toString() == "first") check(release.await(WAIT_SECONDS, TimeUnit.SECONDS))
                synchronized(order) { order += "end ${workspace.fileName}" }
                running.decrementAndGet()
                result
            },
        )
        val first = Thread({ scanIn(runner, "first") }, "first")
        val second = Thread({ scanIn(runner, "second") }, "second")

        first.start()
        assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
        second.start()
        awaitWaiting(second)
        release.countDown()
        first.join()
        second.join()

        assertEquals(0, overlaps.get())
        assertEquals(listOf("start first", "end first", "start second", "end second"), order)
    }

    @Test
    fun `a failed scan lets the next one run and its failure reaches its caller`() {
        val failure = IllegalStateException("scan failed")
        val runner = SingleScanRunner(
            ScanRunner { workspace, _, _, _ ->
                if (workspace.fileName.toString() == "bad") throw failure else result
            },
        )

        val thrown = runCatching { scanIn(runner, "bad") }.exceptionOrNull()

        assertEquals(failure, thrown)
        assertEquals(result, scanIn(runner, "good"))
    }

    private companion object {
        const val WAIT_SECONDS = 60L
    }
}
