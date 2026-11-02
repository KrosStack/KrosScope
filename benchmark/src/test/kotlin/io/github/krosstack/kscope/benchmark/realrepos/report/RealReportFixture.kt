package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.context.ContextAggregates
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextGoldSpec
import io.github.krosstack.kscope.benchmark.context.ContextMethodResult
import io.github.krosstack.kscope.benchmark.context.ContextScores
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.FullFileCoverageCosts
import io.github.krosstack.kscope.benchmark.context.TaskContextResult
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationMethodResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationScores
import io.github.krosstack.kscope.benchmark.localization.TaskLocalization
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryManifest
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositorySpec
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit
import io.github.krosstack.kscope.benchmark.realrepos.RealTimings
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.ScanResult

internal object RealReportFixture {
    const val PRIVATE = "PRIVATE_ACCOUNT_PATH_STATEMENT_SOURCE"
    val tasks = listOf(
        task("a", "one", RealSplit.DEV),
        task("a", "two", RealSplit.DEV),
        task("a", "three", RealSplit.TEST),
        task("b", "four", RealSplit.DEV),
        task("b", "five", RealSplit.TEST),
    )
    val manifest = RealRepositoryManifest(
        listOf(
            RealRepositorySpec("a", "C:/$PRIVATE/clone", "head"),
            RealRepositorySpec("b", "C:/$PRIVATE/clone", "head"),
        ),
        tasks,
        mapOf("a" to mapOf("bot" to 2), "b" to mapOf("formatting" to 3)),
        PRIVATE,
    )

    fun measurements(budgets: List<TokenCount> = ContextBenchmarkBudgets.values): List<RealRepositoryMeasurement> =
        tasks.mapIndexed { index, task ->
            val full = when (index) {
                0 -> 0.2
                1 -> 0.8
                3 -> 1.0
                else -> 0.4
            }
            measurement(task, full, budgets)
        }

    fun measurement(
        task: RealCommitTask,
        full: Double,
        budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
    ): RealRepositoryMeasurement {
        val local = TaskLocalization(
            task.id,
            PRIVATE,
            BenchmarkMethod.entries.associateWith { method ->
                val value = if (method == BenchmarkMethod.FULL) full else 0.1
                LocalizationMethodResult(
                    LocalizationScores(value, value, value, value, value, value, value, value),
                    listOf(PRIVATE),
                    listOf(PRIVATE),
                )
            },
            emptyList(),
        )
        val context = ContextSetResult(
            BenchmarkSet.ORIGINAL,
            budgets.map { budget ->
                ContextAggregates.budget(
                    budget,
                    listOf(contextTask(task, full, budget)),
                    ContextBenchmarkOptions.DEFAULT_METHODS,
                )
            },
        )
        return RealRepositoryMeasurement(
            task, PRIVATE, RealMeasurementStatus.COMPLETE,
            RealTimings(fullScanMillis = 100, localizationMillis = 10, contextMillis = 20, incrementalMillis = 50),
            LocalizationCorpusSummary(3, 1, 2, 3, 4), LocalizationBenchmark.aggregate(listOf(local)), context,
            scan = scan(), update = scan(),
        )
    }

    private fun contextTask(task: RealCommitTask, full: Double, budget: TokenCount): TaskContextResult {
        val methods = ContextBenchmarkOptions.DEFAULT_METHODS.associateWith { method ->
            val value = if (method == ContextBenchmarkMethod.FULL_COMPILER) full else 0.1
            val scores = ContextScores(
                value, value, null, value, value, TokenCount(20), TokenCount(10),
                listOf(PRIVATE), listOf(PRIVATE), emptyList(),
            )
            val context = ContextPackage(
                PRIVATE,
                ContextLevel.RECOMMENDED,
                budget,
                TokenizerId("test"),
                TokenCount(20),
                emptyList(),
                markdown = PRIVATE,
            )
            ContextMethodResult(scores, context, FullFileCoverageCosts())
        }
        return TaskContextResult(
            task.id,
            PRIVATE,
            ContextGoldSpec(task.goldFiles),
            methods,
            resolvedGoldFiles = task.goldFiles,
        )
    }

    private fun task(repository: String, id: String, split: RealSplit): RealCommitTask = RealCommitTask(
        "$repository:$id",
        repository,
        "parent",
        "commit-$id",
        1,
        PRIVATE,
        listOf(PRIVATE),
        split,
    )

    private fun scan() = ScanResult(
        1,
        1,
        0,
        0,
        DiagnosticSummary(0, 0, 0, emptyList()),
        null,
        emptyMap(),
        "C:/$PRIVATE/index",
    )
}
