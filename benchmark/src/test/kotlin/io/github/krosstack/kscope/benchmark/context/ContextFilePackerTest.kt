package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.context.TokenCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextFilePackerTest {
    private val estimator = CountingEstimator()
    private val file = ArtifactRef(RepositoryId("test"), RepoPath("source"))

    @Test
    fun `whole file baseline includes all wrapper tokens and truncates actual source to the total budget`() {
        val document = SourceDocument(file, "0123456789".repeat(40))
        val packer = ContextFilePacker(mapOf(file to document), estimator)
        val full = packer.pack("task", listOf("source"), TokenCount(2_000))
        val partial = packer.pack("task", listOf("source"), TokenCount(100))

        assertEquals(1, full.fragments.size)
        assertEquals(document.text, full.fragments.single().text)
        assertEquals(estimator.count(full.markdown), full.totalTokens)
        assertTrue(full.totalTokens.value > full.fragments.single().tokens.value)
        assertTrue(partial.totalTokens.value <= 100)
        assertEquals(estimator.count(partial.markdown), partial.totalTokens)
        assertTrue(partial.fragments.single().text.isNotEmpty())
        assertTrue(document.text.startsWith(partial.fragments.single().text))
        assertTrue(partial.fragments.single().text.length < document.text.length)
    }

    @Test
    fun `full coverage costs charge earlier nongold files and report unreachable targets`() {
        val other = file.copy(path = RepoPath("earlier"))
        val missing = file.copy(path = RepoPath("unranked"))
        val documents = mapOf(
            other to SourceDocument(other, "noise"),
            file to SourceDocument(file, "gold source"),
            missing to SourceDocument(missing, "unranked source"),
        )
        val packer = ContextFilePacker(documents, estimator)
        val paths = listOf("earlier", "source")
        val prefix = packer.pack("task", paths, TokenCount(2_000))
        val costs = packer.costs("task", paths, setOf(file))
        val unreachable = packer.costs("task", paths, setOf(file, missing))

        assertEquals(prefix.totalTokens, costs.tokens90)
        assertEquals(prefix.totalTokens, costs.tokens100)
        assertNull(costs.reason90)
        assertNull(unreachable.tokens90)
        assertNull(unreachable.tokens100)
        assertNotNull(unreachable.reason100)
    }
}
