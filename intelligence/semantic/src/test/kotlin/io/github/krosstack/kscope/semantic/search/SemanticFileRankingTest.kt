package io.github.krosstack.kscope.semantic.search

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.semantic.store.Quantized
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SemanticFileRankingTest {
    @Test
    fun `a file takes its maximum scaled quantized dot product`() {
        val entries = listOf(entry("a", 10, 0.01f), entry("a", 3, 0.1f), entry("b", 7, 0.05f))
        assertEquals(listOf(ref("b"), ref("a")), SemanticFileRanking.rank(entries, floatArrayOf(1f)))
    }

    @Test
    fun `every supplied artifact receives the entry score`() {
        val shared = SemanticFileRanking.Entry(Quantized(byteArrayOf(2), 1f), setOf(ref("own"), ref("reference")))
        assertEquals(
            listOf(ref("own"), ref("reference"), ref("lower")),
            SemanticFileRanking.rank(listOf(entry("lower", 1, 1f), shared), floatArrayOf(1f)),
        )
    }

    @Test
    fun `zero and negative scores remain ordered candidates with deterministic ties`() {
        val other = ArtifactRef(RepositoryId("other"), RepoPath("zero"))
        val entries = listOf(entry("negative", -1, 1f), entry("zero", 0, 0f), entry("positive", 1, 1f)) +
            SemanticFileRanking.Entry(Quantized(byteArrayOf(0), 0f), setOf(other))
        assertEquals(
            listOf(ref("positive"), other, ref("zero"), ref("negative")),
            SemanticFileRanking.rank(entries, floatArrayOf(1f)),
        )
        assertEquals(emptyList(), SemanticFileRanking.rank(emptyList(), floatArrayOf(1f)))
    }

    private fun entry(path: String, value: Byte, scale: Float) =
        SemanticFileRanking.Entry(Quantized(byteArrayOf(value), scale), setOf(ref(path)))

    private fun ref(path: String) = ArtifactRef(RepositoryId("sample"), RepoPath(path))
}
