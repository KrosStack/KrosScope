package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceDocumentTest {
    private val file = ArtifactRef(RepositoryId("test"), RepoPath("source"))

    @Test
    fun `source positions use exclusive UTF16 columns and retain CRLF and trailing empty lines`() {
        val source = SourceDocument(file, "a😀b\r\nc\n")

        assertEquals(SourceInterval(1, 3), source.interval(TextRange(1, 2, 1, 4)))
        assertEquals(SourceInterval(0, 8), source.interval(TextRange(1, 1, 3, 1)))
        assertEquals(TextRange(3, 1, 3, 1), source.range(SourceInterval(8, 8)))
        assertEquals(TextRange(1, 1, 3, 1), source.fullRange())
        assertNull(source.interval(TextRange(1, 3, 1, 4)))
        assertNull(source.interval(TextRange(4, 1, 4, 2)))
    }
}
