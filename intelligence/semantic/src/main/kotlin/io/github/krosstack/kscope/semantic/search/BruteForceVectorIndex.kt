package io.github.krosstack.kscope.semantic.search

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.semantic.chunk.ChunkKind
import io.github.krosstack.kscope.semantic.store.Quantized
import io.github.krosstack.kscope.spi.embedding.VectorHit
import io.github.krosstack.kscope.spi.embedding.VectorIndex
import io.github.krosstack.kscope.spi.search.SearchScope

public class BruteForceVectorIndex(private val chunks: List<ScoredChunk>) : VectorIndex {
    override fun nearest(query: FloatArray, scope: SearchScope, limit: Int): List<VectorHit> {
        val quantized = Quantized.quantize(query)
        val hits = chunks.filter { (it.chunk.kind == ChunkKind.FILE) == (scope == SearchScope.ARTIFACTS) }
            .flatMap { entry -> entry.chunk.nodes.map { VectorHit(it, entry.vector.dot(quantized)) } }
        return hits.groupBy { it.node }.values.map { candidates -> candidates.maxBy { it.score } }
            .sortedWith(compareByDescending<VectorHit> { it.score }.thenBy { nodeKey(it.node) })
            .take(limit)
    }

    private fun nodeKey(node: NodeRef): String = when (node) {
        is NodeRef.OfSymbol -> "symbol:${node.id.value}"
        is NodeRef.OfArtifact -> "artifact:${node.ref.repository.value}:${node.ref.path.value}"
        is NodeRef.OfModule -> "module:${node.id.value}"
        is NodeRef.OfSourceSet -> "source-set:${node.id.value}"
        is NodeRef.OfExternal -> "external:${node.coordinate}"
    }
}
