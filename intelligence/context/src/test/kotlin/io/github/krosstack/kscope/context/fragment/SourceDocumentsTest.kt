package io.github.krosstack.kscope.context.fragment

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.context.ContextOmissionReason
import kotlin.test.Test
import kotlin.test.assertEquals

internal class SourceDocumentsTest {
    @Test
    fun `changed source content rejects indexed ranges as stale`() {
        val file = sourceArtifact("source/Sample", "fun old() = 1\n")
        val fixture = ContextFixture(listOf(file), emptyList(), mapOf(file.ref to "fun changed() = 2\n"))

        val read = SourceDocuments(fixture).read(file)

        assertEquals(DocumentRead.Failed(ContextOmissionReason.STALE_INDEX), read)
    }

    @Test
    fun `oversized indexed artifacts are rejected before reading content`() {
        val file = sourceArtifact("source/Sample", "small").copy(sizeBytes = 2L * 1024 * 1024 + 1)
        val fixture = ContextFixture(listOf(file), emptyList(), mapOf(file.ref to "small"))

        val read = SourceDocuments(fixture).read(file)

        assertEquals(DocumentRead.Failed(ContextOmissionReason.OVERSIZED), read)
        assertEquals(0, fixture.reads.getOrDefault(file.ref, 0))
    }
}
