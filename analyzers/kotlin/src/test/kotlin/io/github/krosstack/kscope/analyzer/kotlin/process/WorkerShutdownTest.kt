package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.process.ModuleSamples.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkerShutdownTest {
    private val worker = FakeWorkerHarness()

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    @Test
    fun `closing ends the worker and closing twice is harmless`() {
        val process = worker.started("ok")
        assertTrue(process.isRunning)
        process.close()
        process.close()
        assertFalse(process.isRunning)
        worker.assertProcessEnded()
    }

    @Test
    fun `a worker that ignores the shutdown request is destroyed on close`() {
        val process = worker.started("deaf", WorkerTimeouts(1.minutes, 1.minutes, 500.milliseconds))
        process.close()
        worker.assertProcessEnded()
    }

    @Test
    fun `a worker that stops answering is destroyed after the response timeout`() {
        val process = worker.started("hang", WorkerTimeouts(1.minutes, 2.seconds, 10.seconds))
        val (outcome, received) = worker.analyse(process, module("one", "a", "b"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "did not answer within 2s")
        assertEquals(1, received.size)
        process.close()
        worker.assertProcessEnded()
    }

    @Test
    fun `a message that cannot be read fails the request and destroys the worker`() {
        val process = worker.started("garbage")
        val (outcome, _) = worker.analyse(process, module("one", "a"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "cannot be read")
        process.close()
        worker.assertProcessEnded()
    }
}
