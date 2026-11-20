package io.github.krosstack.kscope.benchmark.realrepos.semantic

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.search.ScoredChunk
import io.github.krosstack.kscope.semantic.search.SemanticFileRanking
import io.github.krosstack.kscope.semantic.store.GenerationFile
import io.github.krosstack.kscope.semantic.store.GenerationHeader
import io.github.krosstack.kscope.semantic.store.Quantized
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductRankingReferenceTest {
    @Test
    fun `product kernels match complete frozen lists including ties duplicates and a rank eleven overlap`() {
        val first = (1..10).map { "first$it" } + "overlap"
        val second = (1..10).map { "semantic$it" } + "overlap"
        val cases = listOf(
            first to second,
            listOf("b", "b", "a") to listOf("a", "b"),
            first to emptyList(),
            emptyList<String>() to second,
            emptyList<String>() to emptyList(),
        )
        for ((structural, semantic) in cases) {
            val actual = FileRankFusion.fuse(structural.map(::ref), semantic.map(::ref)).map { it.artifact.path.value }
            assertEquals(SemanticRanking.fuse(structural, semantic), actual)
            assertEquals((structural + semantic).distinct().size, actual.size)
        }
        assertEquals("overlap", SemanticRanking.fuse(first, second).take(10).first())
        assertTrue("semantic1" in SemanticRanking.fuse(first, second))
    }

    @Test
    fun `all chunk kinds credit own and artifact references without deriving symbol artifacts`() {
        val chunks = (ChunkKind.entries + List(12) { ChunkKind.FILE }).mapIndexed { index, kind ->
            ScoredChunk(
                Chunk(
                    "chunk$index",
                    kind,
                    "",
                    listOf(NodeRef.OfArtifact(ref("shared")), NodeRef.OfSymbol(SymbolId("symbol"))),
                    ref("${kind.name}-$index"),
                ),
                Quantized(byteArrayOf((index - 2).toByte()), 0.25f),
            )
        }
        val generation = GenerationFile(
            GenerationHeader("fake", FakeSemanticProvider().identity.copy(dimensions = 1), 1, chunks.size),
            chunks,
        )
        val entries = generation.chunks.map {
            SemanticFileRanking.Entry(
                it.vector,
                (
                    it.chunk.nodes.filterIsInstance<NodeRef.OfArtifact>().map { node -> node.ref } +
                        it.chunk.artifact
                    ).toSet(),
            )
        }
        val query = floatArrayOf(0.333f)
        val actual = SemanticFileRanking.rank(entries, query).map { it.path.value }
        assertEquals(SemanticRanking.semantic(generation, query), actual)
        assertEquals(chunks.map { it.chunk.artifact.path.value }.toSet() + "shared", actual.toSet())
        assertEquals(emptyList(), SemanticFileRanking.rank(emptyList(), query))
    }

    private fun ref(path: String) = ArtifactRef(RepositoryId("sample"), RepoPath(path))
}
