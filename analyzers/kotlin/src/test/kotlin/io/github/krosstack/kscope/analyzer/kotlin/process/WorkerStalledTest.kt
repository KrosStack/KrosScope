package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.process.ModuleSamples.module
import org.junit.jupiter.api.Timeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkerStalledTest {
    private val worker = FakeWorkerHarness()

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a worker that stops reading its input fails the request within the response timeout`() {
        val process = worker.started("deaf", WorkerTimeouts(1.minutes, 2.seconds, 10.seconds))
        val request = module("x".repeat(REQUEST_CHARACTERS), "a")

        val (outcome, received) = worker.analyse(process, request)

        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "did not take its request within 2s")
        assertEquals(0, received.size)
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a worker that stopped reading is destroyed with the request that it did not take`() {
        val process = worker.started("deaf", WorkerTimeouts(1.minutes, 2.seconds, 10.seconds))

        worker.analyse(process, module("x".repeat(REQUEST_CHARACTERS), "a"))
        process.close()

        worker.assertProcessEnded()
    }

    private companion object {
        const val REQUEST_CHARACTERS = 4 shl 20
    }
}
