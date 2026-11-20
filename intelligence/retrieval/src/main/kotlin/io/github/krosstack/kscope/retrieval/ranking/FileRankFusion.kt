package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.ArtifactRef

public object FileRankFusion {
    private const val OFFSET = 60

    public data class Match(
        public val artifact: ArtifactRef,
        public val structuralContribution: Double,
        public val semanticContribution: Double,
    ) {
        public val score: Double get() = structuralContribution + semanticContribution
    }

    public fun fuse(first: List<ArtifactRef>, second: List<ArtifactRef>): List<Match> {
        val structural = contributions(first)
        val semantic = contributions(second)
        return (structural.keys + semantic.keys).map {
            Match(it, structural[it] ?: 0.0, semantic[it] ?: 0.0)
        }.sortedWith(
            compareByDescending<Match> { it.score }.thenBy { it.artifact.path.value }
                .thenBy { it.artifact.repository.value },
        )
    }

    private fun contributions(artifacts: List<ArtifactRef>): Map<ArtifactRef, Double> = artifacts.distinct()
        .mapIndexed { index, artifact -> artifact to 1.0 / (OFFSET + index + 1) }.toMap()
}
