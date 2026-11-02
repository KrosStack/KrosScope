package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.worker.BatchParts
import io.github.krosstack.kscope.spi.worker.WorkerProtocol
import io.github.krosstack.kscope.spi.worker.WorkerRequest
import io.github.krosstack.kscope.spi.worker.WorkerResponse
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The facts of a source set leave the worker in messages of bounded size that join into the batch it derived. */
class BatchMessagesTest {
    private val module = SampleWorkspace.singleModule("lang", "relations/lang/main")

    private fun answer(maxFactsPerMessage: Int): List<WorkerResponse> {
        val out = StringWriter()
        WorkerService(ResponseWriter(out), maxFactsPerMessage).analyze(WorkerRequest.Analyze("a-1", listOf(module)))
        return out.toString().lines().filter { it.isNotBlank() }.map(WorkerProtocol::decodeResponse)
    }

    private fun joined(responses: List<WorkerResponse>): List<FactBatch> {
        val assembler = BatchParts.Assembler()
        return responses.filterIsInstance<WorkerResponse.Batch>().mapNotNull(assembler::accept)
    }

    @Test
    fun `a source set with more facts than a message holds is sent in several messages`() {
        val responses = answer(maxFactsPerMessage = 25)
        val batches = responses.filterIsInstance<WorkerResponse.Batch>()
        assertTrue(batches.size > 3, "Expected several messages, got ${batches.size}")
        assertTrue(batches.all { it.batch.facts.size <= 25 })
        assertEquals(batches.indices.toList(), batches.map { it.part })
        assertEquals(List(batches.size - 1) { false } + true, batches.map { it.last })
        assertEquals(WorkerResponse.Completed("a-1"), responses.last())
    }

    @Test
    fun `the messages join into exactly the batch that one message carries`() {
        val whole = joined(answer(maxFactsPerMessage = BatchParts.MAX_FACTS_PER_MESSAGE * 100))
        val parted = joined(answer(maxFactsPerMessage = 25))
        assertEquals(1, whole.size)
        assertIs<FactBatch>(whole.single())
        assertEquals(whole, parted)
    }
}
