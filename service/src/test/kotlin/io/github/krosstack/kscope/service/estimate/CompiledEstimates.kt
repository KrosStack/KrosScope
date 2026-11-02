package io.github.krosstack.kscope.service.estimate

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.TokenCountStore
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.contextInput
import io.github.krosstack.kscope.service.readWorkspaceFiles

/**
 * `analyze` as it was before its estimates were taken from block measures: the package of every level is compiled and
 * its total is read. It uses only the public API of the context module and is the oracle of the identity tests.
 */
internal fun KrosScope.analyzeByCompiling(task: String): AnalysisResult {
    val result = analyze(task, includeContextEstimates = false)
    val store = tokenCounts ?: TokenCountStore.NONE
    val compiler = ContextCompiler(graph(), readWorkspaceFiles(), TokenEstimators.create(), store)
    val input = result.contextInput()
    val estimates = ContextLevel.entries.associateWith { compiler.compile(input, it).totalTokens }
    tokenCounts?.flush()
    return result.copy(contextEstimates = estimates)
}
