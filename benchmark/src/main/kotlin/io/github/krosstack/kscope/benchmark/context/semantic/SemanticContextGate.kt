package io.github.krosstack.kscope.benchmark.context.semantic

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.ContextTaskEvaluator
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRecord
import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.benchmark.stats.ComparisonReport
import io.github.krosstack.kscope.benchmark.stats.PairedBootstrap
import io.github.krosstack.kscope.benchmark.stats.PairedValue
import kotlinx.serialization.Serializable
import java.math.BigDecimal

internal object SemanticContextGate {
    @Serializable
    enum class Failure { TASKS, ARMS, BUDGETS, SOURCE, IDENTITY, SEMANTIC, INCOMPLETE, REGRESSION, ON_TIER1 }

    @Serializable
    data class Budget(
        val repository: String,
        val set: BenchmarkSet,
        val tokens: Int,
        val comparison: ComparisonReport,
        val delta: String?,
        val pooled: Boolean = false,
    )

    @Serializable
    data class Result(
        val failures: List<Failure>,
        val budgets: List<Budget>,
        val onTier1Passed: Boolean?,
        val regressionPassed: Boolean,
        val cellsBelowLimit: Int = 0,
        val repositoriesBelowLimit: Int = 0,
    ) {
        val passed: Boolean get() = failures.isEmpty()
    }

    private data class Input(val expected: Set<String>, val pipeline: ContextPipelineIdentity?, val repository: String)
    private data class Row(val repository: String, val set: BenchmarkSet, val tokens: Int, val task: TaskContextResult)

    fun evaluate(
        records: List<SemanticContextRecord>,
        expected: List<RealCommitTask> = records.map {
            it.task
        },
    ): Result {
        val failures = mutableSetOf<Failure>()
        if (expected.isEmpty() || records.map { it.task }.toSet() != expected.toSet()) failures += Failure.TASKS
        if (expected.map { it.repository to it.id }.distinct().size != expected.size ||
            records.map { it.task.repository to it.task.id }.distinct().size != records.size
        ) {
            failures += Failure.TASKS
        }
        if (records.map { it.identity }.distinct().size > 1) failures += Failure.IDENTITY
        val rows = records.sortedWith(compareBy({ it.task.repository }, { it.task.id })).flatMap { record ->
            if (!record.complete) failures += Failure.INCOMPLETE
            if (record.inventory == null) failures += Failure.SOURCE
            if (record.identity.value.isBlank()) failures += Failure.IDENTITY
            rows(
                listOfNotNull(record.original, record.paraphrase),
                Input(setOf(record.task.id), record.pipeline, record.task.repository),
                failures,
            )
                .takeIf { record.complete }.orEmpty()
        }
        return assess(rows, failures, tier1 = false)
    }

    fun evaluate(
        result: ContextBenchmarkResult,
        expected: Set<String> = result.sets.firstOrNull()?.budgets?.firstOrNull()?.tasks?.map {
            it.id
        }?.toSet().orEmpty(),
    ): Result {
        val failures = mutableSetOf<Failure>()
        if (!result.complete || !result.valid || result.validationErrors.isNotEmpty()) failures += Failure.INCOMPLETE
        if (result.schemaVersion != 2) failures += Failure.INCOMPLETE
        val rows = rows(result.sets, Input(expected, result.pipeline, "reference"), failures)
        if (result.sets.any {
                it.budgets.any { budget -> !budget.valid || budget.tasks.any { task -> !task.valid } }
            }
        ) {
            failures += Failure.SOURCE
        }
        return assess(rows, failures, tier1 = true)
    }

    fun markdown(result: Result): String = buildString {
        appendLine("ON/OFF regression: ${if (result.regressionPassed) "PASS" else "FAIL"}.")
        appendLine("ON Tier 1: ${result.onTier1Passed?.let { if (it) "PASS" else "FAIL" } ?: "NOT RUN"}.")
        appendLine("Failures: ${result.failures.joinToString().ifEmpty { "none" }}.")
        appendLine(
            "Per-repository cells below the limit: ${result.cellsBelowLimit} in " +
                "${result.repositoriesBelowLimit} repositories (evidence; the pooled comparison decides).",
        )
        appendLine()
        appendLine("| Repository | Set | Budget | Metric | ON | OFF | Delta | Lower 95% | Upper 95% |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        result.budgets.forEach { row ->
            val comparison = row.comparison
            val repository = if (row.pooled) "pooled (all repositories)" else row.repository
            appendLine(
                "| $repository | ${row.set} | ${row.tokens} | ${comparison.metric} | " +
                    "${comparison.candidateMean} | ${comparison.baselineMean} | ${row.delta} | " +
                    "${comparison.lower95} | ${comparison.upper95} |",
            )
        }
    }

    private fun rows(sets: List<ContextSetResult>, input: Input, failures: MutableSet<Failure>): List<Row> {
        if (sets.map { it.kind }.sorted() != BenchmarkSet.entries || input.expected.isEmpty()) failures += Failure.TASKS
        if (!identityComplete(input.pipeline)) failures += Failure.IDENTITY
        return sets.flatMap { set ->
            if (set.budgets.map { it.budget }.sortedBy { it.value } != ContextBenchmarkBudgets.values) {
                failures += Failure.BUDGETS
            }
            set.budgets.flatMap { budget ->
                if (budget.tasks.map { it.id }.sorted() != input.expected.sorted()) failures += Failure.TASKS
                budget.tasks.map { task ->
                    validate(task, input.pipeline, failures)
                    Row(input.repository, set.kind, budget.budget.value, task)
                }
            }
        }
    }

    private fun identityComplete(pipeline: ContextPipelineIdentity?): Boolean {
        if (pipeline == null) return false
        val identities = listOf(
            pipeline.provider,
            pipeline.model.name,
            pipeline.model.revision,
            pipeline.implementation.value,
            pipeline.generation?.value,
        )
        val payloads = listOf(pipeline.workerFiles, pipeline.modelFiles, pipeline.runtime, pipeline.policy)
        return identities.all { !it.isNullOrBlank() } && payloads.all { it.isNotEmpty() }
    }

    private fun validate(task: TaskContextResult, pipeline: ContextPipelineIdentity?, failures: MutableSet<Failure>) {
        if (!task.methods.keys.containsAll(listOf(OFF, ON))) failures += Failure.ARMS
        if (!ContextTaskEvaluator.complete(task.methods[ON]?.semantic, pipeline)) failures += Failure.SEMANTIC
        if (task.methods[OFF]?.semantic != io.github.krosstack.kscope.retrieval.SemanticStatus.OFF) {
            failures += Failure.SEMANTIC
        }
        if (task.methods.values.any { arm ->
                arm.scores.sourceIssues.isNotEmpty() ||
                    listOf(
                        arm.scores.goldContextRecall,
                        arm.scores.tokenPrecision,
                    ).any { !it.isFinite() || it !in 0.0..1.0 }
            }
        ) {
            failures += Failure.SOURCE
        }
    }

    private fun assess(rows: List<Row>, failures: MutableSet<Failure>, tier1: Boolean): Result {
        val paired = rows.filter { it.task.methods.keys.containsAll(listOf(OFF, ON)) }
        val groups = paired.groupBy { it.repository }.toSortedMap().map { it.key to it.value } + listOf(null to paired)
        val budgets = groups.flatMap { (repository, group) ->
            group.groupBy { it.set to it.tokens }.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))
                .flatMap { (key, measured) ->
                    METRICS.map { metric ->
                        val pairs = measured.map {
                            PairedValue("${it.repository}/${it.task.id}", value(it, ON, metric), value(it, OFF, metric))
                        }
                        val comparison = PairedBootstrap.compare(metric, pairs, "FULL ON", "FULL OFF")
                        val delta = comparison.candidateMean?.toBigDecimal()
                            ?.subtract(comparison.baselineMean?.toBigDecimal())
                        val name = repository ?: "pooled"
                        val pooled = repository == null
                        Budget(name, key.first, key.second, comparison, delta?.toPlainString(), pooled = pooled)
                    }
                }
        }
        if (budgets.any { it.delta == null || (it.pooled && belowLimit(it)) }) failures += Failure.REGRESSION
        val cellsBelowLimit = budgets.filter { !it.pooled && belowLimit(it) }
        val regressionPassed = failures.isEmpty()
        val onTier1 = if (tier1) tier1(rows, failures) else null
        return Result(
            failures.sortedBy { it.ordinal },
            budgets,
            onTier1,
            regressionPassed,
            cellsBelowLimit = cellsBelowLimit.size,
            repositoriesBelowLimit = cellsBelowLimit.map { it.repository }.distinct().size,
        )
    }

    private fun belowLimit(budget: Budget): Boolean =
        budget.delta?.toBigDecimal()?.let { it < BigDecimal("-0.01") } == true

    private fun tier1(rows: List<Row>, failures: MutableSet<Failure>): Boolean {
        val complete = rows.isNotEmpty() && rows.all { it.task.methods.keys.containsAll(listOf(ON) + BASELINES) }
        if (!complete) failures += Failure.ARMS
        val passed = complete && rows.groupBy { it.set to it.tokens }.values.all { group ->
            METRICS.all { metric ->
                BASELINES.all { baseline ->
                    group.map { value(it, ON, metric) }.average() > group.map { value(it, baseline, metric) }.average()
                }
            }
        } && failures.none { it != Failure.REGRESSION }
        if (!passed) failures += Failure.ON_TIER1
        return passed
    }

    private fun value(row: Row, method: ContextBenchmarkMethod, metric: ComparisonMetric): Double =
        if (metric == ComparisonMetric.GOLD_CONTEXT_RECALL) {
            row.task.methods.getValue(method).scores.goldContextRecall
        } else {
            row.task.methods.getValue(method).scores.tokenPrecision
        }

    private val OFF = ContextBenchmarkMethod.FULL_COMPILER
    private val ON = ContextBenchmarkMethod.FULL_SEMANTIC_COMPILER
    private val BASELINES = listOf(ContextBenchmarkMethod.BM25_RAW, ContextBenchmarkMethod.BM25_ENGLISH)
    private val METRICS = listOf(ComparisonMetric.GOLD_CONTEXT_RECALL, ComparisonMetric.TOKEN_PRECISION)
}
