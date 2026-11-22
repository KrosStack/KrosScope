package io.github.krosstack.kscope.service.semantic.query

import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.service.KrosScope

internal fun KrosScope.withSemanticRanking(
    result: AnalysisResult,
    stage: RetrievalStage,
    ml: MlMode,
    retrieval: TaskRetrieval? = null,
): AnalysisResult {
    if (stage != RetrievalStage.FULL || ml != MlMode.ON) return result
    val revision = memory.revision
    return when (val outcome = semantic.rank(result.query.text, ml)) {
        is SemanticQueryOutcome.Skipped -> result

        is SemanticQueryOutcome.Applied -> {
            if (memory.revision != revision || outcome.snapshotIdentity.graphRevision != revision) {
                semantic.invalidate()
                result
            } else {
                val fused = FileRankFusion.fuse(result.files.map { it.artifact }, outcome.files)
                val applied = (retrieval ?: TaskRetrieval(graph(), searchIndex())).withFileRanking(result, fused)
                if (memory.revision != revision) {
                    semantic.invalidate()
                    result
                } else {
                    applied.copy(semantic = outcome.status)
                }
            }
        }
    }
}
