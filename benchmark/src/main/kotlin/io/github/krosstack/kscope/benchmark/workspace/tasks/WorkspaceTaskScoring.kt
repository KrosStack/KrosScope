package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.context.ContextAggregates
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationSetResult

/** Turns the per-task results of one run into means over any set of its tasks. */
internal class WorkspaceTaskScoring(
    private val localization: LocalizationSetResult,
    private val context: ContextSetResult,
    private val contextMethods: List<ContextBenchmarkMethod>,
) {
    fun missingGoldFiles(id: String): List<String> = localization.tasks.single { it.id == id }.missingGoldFiles

    fun over(ids: Set<String>): MeasuredScores = MeasuredScores(localizationRows(ids), contextRows(ids))

    private fun localizationRows(ids: Set<String>): List<LocalizationRow> =
        LocalizationBenchmark.aggregate(localization.tasks.filter { it.id in ids }).methods
            .map { (method, scores) -> LocalizationRow(method, scores.recall5, scores.recall10, scores.ndcg10) }

    private fun contextRows(ids: Set<String>): List<ContextRow> = context.budgets.flatMap { measured ->
        val tasks = measured.tasks.filter { it.id in ids }
        ContextAggregates.budget(measured.budget, tasks, contextMethods).methods.map { (method, scores) ->
            ContextRow(method, measured.budget.value, scores.goldContextRecall, scores.tokenPrecision)
        }
    }
}
