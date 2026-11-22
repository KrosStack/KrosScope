package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind

internal object FusedAnalysisFiles {
    fun build(structural: List<RankedFile>, fused: List<FileRankFusion.Match>): List<RankedFile> {
        val originals = structural.associateBy { it.artifact }
        return fused.take(LIMIT).map { match ->
            val original = originals[match.artifact]
            val reasons = original?.reasons.orEmpty().map { reason ->
                reason.copy(score = reason.score * match.structuralContribution / requireNotNull(original).score)
            } + if (match.semanticContribution > 0.0) {
                listOf(SignalContribution(SignalKind.SEMANTIC, match.semanticContribution))
            } else {
                emptyList()
            }
            RankedFile(match.artifact, match.score, original?.role ?: FileRole.READ, reasons)
        }
    }

    private const val LIMIT = 10
}
