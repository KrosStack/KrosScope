package io.github.krosstack.kscope.benchmark.realrepos

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextScores
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import io.github.krosstack.kscope.benchmark.localization.TaskLocalization
import io.github.krosstack.kscope.model.context.TokenCount

internal object RealMeasurementShape {
    fun localization(record: RealRepositoryMeasurement): TaskLocalization? {
        val task = record.localization?.tasks?.singleOrNull()
        return task?.takeIf {
            it.validGold && it.id == record.task.id && it.statement == record.task.statement &&
                record.task.goldFiles.containsAll(it.missingGoldFiles) &&
                it.methods.keys == BenchmarkMethod.entries.toSet() &&
                it.methods.values.all { method -> finite(method.scores) }
        }
    }

    fun context(record: RealRepositoryMeasurement, budget: TokenCount): TaskContextResult? {
        val task = record.context?.budgets?.singleOrNull { it.budget == budget }?.tasks?.singleOrNull()
        return task?.takeIf { identity(it, record.task) && methods(it, budget) }
    }

    fun budgets(record: RealRepositoryMeasurement): Boolean = record.context?.budgets?.map { it.budget }?.let {
        it.isNotEmpty() && it.distinct().size == it.size && ContextBenchmarkBudgets.values.containsAll(it)
    } == true

    fun recordedBudgets(record: RealRepositoryMeasurement): Set<TokenCount> =
        record.context?.budgets?.map { it.budget }?.toSet().orEmpty()

    fun sourceIssues(context: ContextSetResult?): Boolean = context?.budgets.orEmpty().any { budget ->
        budget.tasks.any { task -> task.methods.values.any { it.scores.sourceIssues.isNotEmpty() } }
    }

    private fun methods(task: TaskContextResult, budget: TokenCount): Boolean =
        task.methods.keys == ContextBenchmarkOptions.DEFAULT_METHODS.toSet() && task.methods.values.all { method ->
            method.scores.sourceIssues.isEmpty() && finite(method.scores) && method.context.budget == budget &&
                method.scores.totalTokens == method.context.totalTokens &&
                method.scores.totalTokens.value <= budget.value
        }

    private fun identity(context: TaskContextResult, task: RealCommitTask): Boolean =
        context.id == task.id && context.statement == task.statement && task.statement.isNotBlank() &&
            context.gold.symbols.isEmpty() && context.gold.files.toSet() == task.goldFiles.toSet() &&
            accounted(context, task) && context.unmappedSymbols.isEmpty()

    private fun accounted(context: TaskContextResult, task: RealCommitTask): Boolean {
        val resolved = context.resolvedGoldFiles.toSet()
        val absent = context.excludedMissingFiles.toSet()
        return resolved.size == context.resolvedGoldFiles.size && absent.size == context.excludedMissingFiles.size &&
            resolved.intersect(absent).isEmpty() && resolved + absent == task.goldFiles.toSet()
    }

    private fun finite(scores: LocalizationScores): Boolean = listOf(
        scores.recall1,
        scores.recall3,
        scores.recall5,
        scores.recall10,
        scores.ndcg10,
        scores.averagePrecision,
        scores.editPrecision,
        scores.contextRecall10,
    ).all { it.isFinite() && it in 0.0..1.0 }

    private fun finite(scores: ContextScores): Boolean = listOf(
        scores.goldContextRecall,
        scores.fileRecall,
        scores.fullFileRecall,
        scores.tokenPrecision,
    ).all { it.isFinite() && it in 0.0..1.0 } && scores.symbolRecall == null
}
