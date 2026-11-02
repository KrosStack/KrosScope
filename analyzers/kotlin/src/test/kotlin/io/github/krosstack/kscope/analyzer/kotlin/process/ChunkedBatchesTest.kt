package io.github.krosstack.kscope.analyzer.kotlin.process

import io.github.krosstack.kscope.analyzer.kotlin.process.ModuleSamples.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChunkedBatchesTest {
    private val worker = FakeWorkerHarness()

    @AfterTest
    fun cleanUp() {
        worker.close()
    }

    @Test
    fun `a batch that arrives in several messages reaches the caller as one batch with all its facts`() {
        val process = worker.started("parts")
        val (outcome, received) = worker.analyse(process, module("one", "a", "b"))
        assertEquals(WorkerOutcome.Completed, outcome)
        assertEquals(listOf("a", "b"), received.map { it.owner.key.substringAfterLast('@') })
        assertTrue(received.all { it.facts.size == FakeBatches.PART_FACTS })
    }

    @Test
    fun `a message that skips a part fails the request and ends the worker`() {
        val process = worker.started("parts-gap")
        val (outcome, received) = worker.analyse(process, module("one", "a"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "order of its messages")
        assertTrue(received.isEmpty())
        process.close()
        worker.assertProcessEnded()
    }

    @Test
    fun `a request that completes in the middle of a batch fails and delivers nothing of that batch`() {
        val process = worker.started("parts-unfinished")
        val (outcome, received) = worker.analyse(process, module("one", "a"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "in the middle of the batch")
        assertTrue(received.isEmpty())
    }

    @Test
    fun `a worker that exits in the middle of a batch leaves that batch unanswered`() {
        val process = worker.started("parts-exit")
        val (outcome, received) = worker.analyse(process, module("one", "a"))
        assertContains(assertIs<WorkerOutcome.Failed>(outcome).message, "exited")
        assertTrue(received.isEmpty())
    }
}
