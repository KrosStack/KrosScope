package io.github.krosstack.kscope.retrieval.ranking

internal object RankingWeights {
    const val SYMBOL = 1.0
    const val ARTIFACT = 1.0
    const val GRAPH = 0.8
    const val CATEGORY = 0.35
    const val HISTORY = 0.1
    const val EXACT = 2.0
    const val DAMPING = 0.15
    const val FUSION_OFFSET = 20.0
}
