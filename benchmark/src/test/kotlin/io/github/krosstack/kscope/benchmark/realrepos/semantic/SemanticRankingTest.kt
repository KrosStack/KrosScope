package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationHeader
import io.github.krosstack.kscope.semantic.store.Quantized
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SemanticRankingTest {
    @Test
    fun `reciprocal ranks are one based deduplicated and tied by path`() {
        assertEquals(listOf("c", "a", "b"), SemanticRanking.fuse(listOf("a", "a", "c"), listOf("b", "c")))
        assertEquals(listOf("a", "b"), SemanticRanking.fuse(listOf("b", "a"), listOf("a", "b")))
    }

    @Test
    fun `file similarity takes the maximum scaled dot product`() {
        val chunks = listOf(scored("a", 10, 0.01f), scored("a", 3, 0.1f), scored("b", 7, 0.05f))
        val header = GenerationHeader("fake", FakeSemanticProvider().identity.copy(dimensions = 1), 1, chunks.size)
        assertEquals(listOf("b", "a"), SemanticRanking.semantic(GenerationFile(header, chunks), floatArrayOf(1f)))
    }

    @Test
    fun `gold absent from the index stays in recall and ideal gain`() {
        val scores = SemanticRanking.metrics(listOf("a"), listOf("a", "absent"))
        assertEquals(1.0, scores[0])
        assertEquals(0.5, scores[1])
        assertEquals(1.0 / (1.0 + 1.0 / kotlin.math.log2(3.0)), scores[2])
    }

    private fun scored(path: String, value: Byte, scale: Float): ScoredChunk {
        val ref = ArtifactRef(RepositoryId("sample"), RepoPath(path))
        val chunk = Chunk("$path-$value", ChunkKind.FILE, "", emptyList(), ref)
        return ScoredChunk(chunk, Quantized(byteArrayOf(value), scale))
    }
}
