package io.github.krosstack.kscope.spi.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatchPartsSplitTest {
    @Test
    fun `a batch that fits is one message that starts and ends the batch`() {
        val parts = BatchParts.split("a-1", sampleBatch("s", 3), maxFacts = 3)
        assertEquals(1, parts.size)
        assertEquals(0, parts.single().part)
        assertTrue(parts.single().last)
    }

    @Test
    fun `an empty batch is one message`() {
        assertEquals(1, BatchParts.split("a-1", sampleBatch("s", 0)).size)
    }

    @Test
    fun `a larger batch is cut into messages of at most the limit and only the final one is last`() {
        val parts = BatchParts.split("a-1", sampleBatch("s", 7), maxFacts = 3)
        assertEquals(listOf(3, 3, 1), parts.map { it.batch.facts.size })
        assertEquals(listOf(0, 1, 2), parts.map { it.part })
        assertEquals(listOf(false, false, true), parts.map { it.last })
        assertTrue(parts.all { it.requestId == "a-1" && it.batch.owner.key == "s" })
    }

    @Test
    fun `the default limit keeps a message to a few hundred facts`() {
        val parts = BatchParts.split("a-1", sampleBatch("s", BatchParts.MAX_FACTS_PER_MESSAGE * 2 + 1))
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.batch.facts.size <= BatchParts.MAX_FACTS_PER_MESSAGE })
    }

    @Test
    fun `a message holds at least one fact`() {
        assertFailsWith<IllegalArgumentException> { BatchParts.split("a-1", sampleBatch("s", 1), maxFacts = 0) }
    }

    @Test
    fun `parts survive encoding and keep their position`() {
        val parts = BatchParts.split("a-1", sampleBatch("s", 5), maxFacts = 2)
        parts.forEach { assertEquals(it, WorkerProtocol.decodeResponse(WorkerProtocol.encode(it))) }
        assertFalse(parts.map(WorkerProtocol::encode).any { '\n' in it })
    }
}
