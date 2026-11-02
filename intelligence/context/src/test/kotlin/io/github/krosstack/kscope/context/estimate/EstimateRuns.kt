package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.context.ContextMarkdown
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextRendering
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal class Block(val persisted: Boolean, val text: String)

internal object EstimateRuns {
    val persisted = setOf(ContextRendering.FILE, ContextRendering.DECLARATION, ContextRendering.ENTRY)

    /** A total that cannot be computed is compared by the kind of failure: both calls must fail the same way. */
    fun outcome(total: () -> TokenCount): Any = runCatching(total).getOrElse { it.javaClass }

    fun estimateEachLevel(compiler: ContextCompiler, case: EstimateCase) {
        for (level in ContextLevel.entries) outcome { compiler.estimate(case.input, level) }
    }

    fun compileAndEstimateEachLevel(compiler: ContextCompiler, case: EstimateCase) {
        for (level in ContextLevel.entries) {
            outcome { compiler.compile(case.input, level).totalTokens }
            outcome { compiler.estimate(case.input, level) }
        }
    }

    fun fragmentBlocksOf(case: EstimateCase, estimator: TokenEstimator): List<Block> =
        ContextLevel.entries.flatMap { level ->
            compiled(case, estimator, level).fragments.map {
                Block(it.rendering in persisted, ContextMarkdown.fragment(it))
            }
        }

    fun fixedBlocksOf(case: EstimateCase, estimator: TokenEstimator): Set<String> =
        ContextLevel.entries.flatMap { level ->
            val compiled = compiled(case, estimator, level)
            ContextMarkdown.blocks(compiled.task, emptyList(), compiled.unresolved)
        }.toSet()

    private fun compiled(case: EstimateCase, estimator: TokenEstimator, level: ContextLevel) =
        ContextCompiler(case.fixture, case.fixture, estimator).compile(case.input, level)
}
