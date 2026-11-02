package io.github.krosstack.kscope.spi.worker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BatchPartsAssemblerTest {
    private fun parts(owner: String, count: Int, maxFacts: Int, fingerprint: String = "f1") =
        BatchParts.split("a-1", sampleBatch(owner, count, fingerprint), maxFacts)

    @Test
    fun `the assembler joins the messages of a batch in order`() {
        val whole = sampleBatch("s", 10)
        val assembler = BatchParts.Assembler()
        val joined = parts("s", 10, maxFacts = 4).mapNotNull(assembler::accept)
        assertEquals(listOf(whole), joined)
        assertNull(assembler.incomplete())
    }

    @Test
    fun `the assembler serves the batches of one request one after the other`() {
        val assembler = BatchParts.Assembler()
        val joined = (parts("one", 5, 2) + parts("two", 2, 2)).mapNotNull(assembler::accept)
        assertEquals(listOf(sampleBatch("one", 5), sampleBatch("two", 2)), joined)
    }

    @Test
    fun `a batch that is not finished is reported as incomplete`() {
        val assembler = BatchParts.Assembler()
        assertNull(assembler.accept(parts("s", 5, 2)[0]))
        assertEquals("s", assembler.incomplete()?.key)
    }

    @Test
    fun `a missing message is a violation`() {
        val assembler = BatchParts.Assembler()
        val messages = parts("s", 6, 2)
        assembler.accept(messages[0])
        assertFailsWith<BatchParts.OutOfSequence> { assembler.accept(messages[2]) }
    }

    @Test
    fun `a batch that starts before the previous one ended is a violation`() {
        val assembler = BatchParts.Assembler()
        assembler.accept(parts("one", 6, 2).first())
        assertFailsWith<BatchParts.OutOfSequence> { assembler.accept(parts("two", 1, 2).single()) }
    }

    @Test
    fun `a continuation of another owner is a violation`() {
        val assembler = BatchParts.Assembler()
        assembler.accept(parts("one", 4, 2)[0])
        assertFailsWith<BatchParts.OutOfSequence> { assembler.accept(parts("two", 4, 2)[1]) }
    }

    @Test
    fun `a continuation with another fingerprint is a violation`() {
        val assembler = BatchParts.Assembler()
        assembler.accept(parts("one", 4, 2)[0])
        assertFailsWith<BatchParts.OutOfSequence> { assembler.accept(parts("one", 4, 2, fingerprint = "f2")[1]) }
    }
}
