package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.context.ContextCandidate
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.ContextRole
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.spi.context.TokenEstimator

public fun KrosScope.context(
    task: String,
    budget: TokenCount? = null,
    level: ContextLevel = ContextLevel.RECOMMENDED,
    estimator: TokenEstimator = TokenEstimators.create(),
    stage: RetrievalStage = RetrievalStage.FULL,
): ContextPackage {
    val analysis = analyze(task, stage, includeContextEstimates = false)
    return progress.stage(QueryStages.COMPILE) {
        val compiler = ContextCompiler(graph(), readWorkspaceFiles(), estimator, countStore())
        compiler.compile(analysis.contextInput(), level, budget).also { tokenCounts?.flush() }
    }
}

internal fun KrosScope.withContextEstimates(result: AnalysisResult): AnalysisResult {
    val compiler = ContextCompiler(graph(), readWorkspaceFiles(), TokenEstimators.create(), countStore())
    val input = result.contextInput()
    val estimates = ContextLevel.entries.associateWith { compiler.estimate(input, it) }
    tokenCounts?.flush()
    return result.copy(contextEstimates = estimates)
}

private fun KrosScope.countStore(): TokenCountStore = tokenCounts ?: TokenCountStore.NONE

public fun AnalysisResult.contextInput(): ContextInput {
    val edits = files.filter { it.role == FileRole.EDIT }.map { it.artifact }.toSet()
    val primary = files.map { file ->
        val role = if (file.role == FileRole.EDIT) ContextRole.EDIT else ContextRole.READ
        ContextCandidate(NodeRef.OfArtifact(file.artifact), file.score, role)
    } + symbols.map {
        val role = if (it.symbol.declaration?.artifact in edits) ContextRole.EDIT else ContextRole.READ
        ContextCandidate(NodeRef.OfSymbol(it.symbol.id), it.score, role)
    }
    val primaryPeak = primary.maxOfOrNull { it.relevance } ?: 1.0
    val testPeak = tests.maxOfOrNull { it.score } ?: 0.0
    val candidates = primary + tests.map {
        val relevance = if (testPeak > 0.0) it.score / testPeak * primaryPeak else 0.0
        ContextCandidate(NodeRef.OfArtifact(it.artifact), relevance, ContextRole.TEST)
    }
    return ContextInput(query.text, candidates, boundaries, tests.map { NodeRef.OfArtifact(it.artifact) }, unresolved)
}
