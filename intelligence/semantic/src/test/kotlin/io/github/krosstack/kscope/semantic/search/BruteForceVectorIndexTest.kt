package io.github.krosstack.kscope.semantic.search

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.semantic.chunk.Chunk
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.store.Quantized
import io.github.krosstack.kscope.spi.search.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals

class BruteForceVectorIndexTest {
    private val artifact = ArtifactRef(RepositoryId("repo"), RepoPath("source.kt"))
    private val a = NodeRef.OfSymbol(SymbolId("a"))
    private val b = NodeRef.OfSymbol(SymbolId("b"))

    @Test
    fun `scores descend ties use node key and groups yield one hit per node`() {
        val group = entry(ChunkKind.GROUP, listOf(b, a), 1f)
        val higher = entry(ChunkKind.DECLARATION, listOf(NodeRef.OfSymbol(SymbolId("c"))), 2f)
        val index = BruteForceVectorIndex(listOf(group, higher))
        assertEquals(
            listOf("c", "a", "b"),
            index.nearest(floatArrayOf(1f), SearchScope.SYMBOLS, 10)
                .map { (it.node as NodeRef.OfSymbol).id.value },
        )
        assertEquals(2, index.nearest(floatArrayOf(1f), SearchScope.SYMBOLS, 2).size)
    }

    @Test
    fun `scope separates file artifact hits and symbol hits`() {
        val index = BruteForceVectorIndex(
            listOf(
                entry(ChunkKind.FILE, listOf(NodeRef.OfArtifact(artifact)), 1f),
                entry(ChunkKind.TYPE, listOf(a), 1f),
            ),
        )
        assertEquals(
            listOf(NodeRef.OfArtifact(artifact)),
            index.nearest(floatArrayOf(1f), SearchScope.ARTIFACTS, 10)
                .map { it.node },
        )
        assertEquals(listOf(a), index.nearest(floatArrayOf(1f), SearchScope.SYMBOLS, 10).map { it.node })
    }

    @Test
    fun `ties compare raw node keys rather than escaped JSON`() {
        val quote = NodeRef.OfSymbol(SymbolId("a\""))
        val hash = NodeRef.OfSymbol(SymbolId("a#"))
        val index = BruteForceVectorIndex(listOf(entry(ChunkKind.GROUP, listOf(hash, quote), 1f)))
        assertEquals(listOf(quote, hash), index.nearest(floatArrayOf(1f), SearchScope.SYMBOLS, 2).map { it.node })
    }

    private fun entry(kind: ChunkKind, nodes: List<NodeRef>, value: Float): ScoredChunk = ScoredChunk(
        Chunk(kind.name, kind, "", nodes, artifact),
        Quantized.quantize(floatArrayOf(value)),
    )
}
