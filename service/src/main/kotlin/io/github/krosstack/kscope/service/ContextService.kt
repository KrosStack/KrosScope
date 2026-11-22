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
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.service.context.ContextRequest
import io.github.krosstack.kscope.service.context.ContextResult
import io.github.krosstack.kscope.service.context.SemanticContextCandidates
import io.github.krosstack.kscope.spi.context.TokenEstimator

public fun KrosScope.context(
    task: String,
    budget: TokenCount? = null,
    level: ContextLevel = ContextLevel.RECOMMENDED,
    estimator: TokenEstimator = TokenEstimators.create(),
    stage: RetrievalStage = RetrievalStage.FULL,
): ContextPackage = context(ContextRequest(task, budget, level, estimator, stage, ml = MlMode.ON))

public fun KrosScope.context(request: ContextRequest): ContextPackage = compileContext(request).context

public fun KrosScope.contextResult(request: ContextRequest): ContextResult = compileContext(request)

private fun KrosScope.compileContext(request: ContextRequest): ContextResult {
    val ranked = rankedAnalysis(
        request.task,
        request.stage,
        includeContextEstimates = false,
        ml = request.ml,
        within = request.within,
    )
    val analysis = ranked.result
    val context = progress.stage(QueryStages.COMPILE) {
        val compiler = ContextCompiler(graph(), readWorkspaceFiles(), request.estimator, countStore())
        compiler.compile(contextInput(analysis, null, ranked.structural), request.level, request.budget)
            .also { tokenCounts?.flush() }
    }
    return ContextResult(context, analysis.semantic, analysis.searchCapped)
}

internal fun KrosScope.withContextEstimates(
    result: AnalysisResult,
    retrieval: TaskRetrieval? = null,
    baseline: AnalysisResult? = null,
): AnalysisResult {
    val compiler = ContextCompiler(graph(), readWorkspaceFiles(), TokenEstimators.create(), countStore())
    val input = contextInput(result, retrieval, baseline)
    val estimates = ContextLevel.entries.associateWith { compiler.estimate(input, it) }
    tokenCounts?.flush()
    return result.copy(contextEstimates = estimates)
}

private fun KrosScope.countStore(): TokenCountStore = tokenCounts ?: TokenCountStore.NONE

public fun KrosScope.contextInput(result: AnalysisResult): ContextInput = contextInput(result, null)

internal fun KrosScope.contextInput(
    result: AnalysisResult,
    retrieval: TaskRetrieval?,
    baseline: AnalysisResult? = null,
): ContextInput = if (result.semantic.state == SemanticState.USED || result.semantic.state == SemanticState.BEHIND) {
    val canonical = retrieval ?: TaskRetrieval(graph(), searchIndex())
    val structural = baseline ?: canonical.analyze(result.query.text, RetrievalStage.FULL, MlMode.OFF)
    SemanticContextCandidates.input(result, structural, canonical::canonicalFile)
} else {
    result.contextInput()
}

/** USED and BEHIND results require [KrosScope.contextInput] and are rejected here. */
public fun AnalysisResult.contextInput(): ContextInput {
    require(semantic.state != SemanticState.USED && semantic.state != SemanticState.BEHIND) {
        "Applied results require KrosScope.contextInput."
    }
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
