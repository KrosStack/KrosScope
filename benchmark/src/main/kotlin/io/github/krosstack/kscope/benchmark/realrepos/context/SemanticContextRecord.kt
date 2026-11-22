package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.ContextTaskEvaluator
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.model.ContentHash
import kotlinx.serialization.Serializable

@Serializable
internal data class SemanticContextRecord(
    val task: RealCommitTask,
    val identity: ContentHash,
    val pipeline: ContextPipelineIdentity? = null,
    val inventory: ContentHash? = null,
    val original: ContextSetResult? = null,
    val paraphrase: ContextSetResult? = null,
    val unresolved: String? = null,
    val failure: String? = null,
    val schemaVersion: Int = 1,
) {
    val complete: Boolean get() = settled && unresolved == null

    val settled: Boolean get() = schemaVersion == 1 && failure == null && original?.kind == BenchmarkSet.ORIGINAL &&
        pipeline?.generation != null && inventory != null && valid(original) && when (unresolved) {
            null -> paraphrase?.kind == BenchmarkSet.HELD_OUT && valid(paraphrase)
            UNRESOLVED_PARAPHRASE -> paraphrase == null
            else -> false
        }

    fun valid(set: ContextSetResult?): Boolean = set != null &&
        set.budgets.map { it.budget } == ContextBenchmarkBudgets.values && set.budgets.all { budget ->
            val result = budget.tasks.singleOrNull()
            budget.valid && result?.valid == true && result.id == task.id &&
                result.methods.keys.toList() == SemanticContextOptions.METHODS &&
                result.methods.values.all { it.scores.sourceIssues.isEmpty() } &&
                ContextTaskEvaluator.complete(
                    result.methods[ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER]?.semantic,
                    pipeline,
                )
        }

    companion object {
        const val UNRESOLVED_PARAPHRASE = "paraphrase"
    }
}
