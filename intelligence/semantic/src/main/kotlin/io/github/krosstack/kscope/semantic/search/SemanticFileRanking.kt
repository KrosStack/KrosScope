package io.github.krosstack.kscope.semantic.search

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.semantic.store.Quantized

public object SemanticFileRanking {
    public data class Entry(public val vector: Quantized, public val artifacts: Set<ArtifactRef>)

    public fun rank(entries: List<Entry>, query: FloatArray): List<ArtifactRef> {
        val quantized = Quantized.quantize(query)
        val scores = mutableMapOf<ArtifactRef, Double>()
        for (entry in entries) {
            val score = entry.vector.dot(quantized)
            for (artifact in entry.artifacts) scores.merge(artifact, score, ::maxOf)
        }
        return scores.keys.sortedWith(
            compareByDescending<ArtifactRef> { scores.getValue(it) }
                .thenBy { it.path.value }.thenBy { it.repository.value },
        )
    }
}
