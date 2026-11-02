package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import kotlinx.serialization.Serializable

@Serializable
internal data class LocalizationRow(
    val method: BenchmarkMethod,
    val recall5: Double,
    val recall10: Double,
    val ndcg10: Double,
)

@Serializable
internal data class ContextRow(
    val method: ContextBenchmarkMethod,
    val budget: Int,
    val goldRecall: Double,
    val tokenPrecision: Double,
)

/** The means of a set of tasks: localization for each method, and context for each method and budget. */
@Serializable
internal data class MeasuredScores(val localization: List<LocalizationRow>, val context: List<ContextRow>)

@Serializable
internal data class TaskScores(
    val id: String,
    val title: String,
    val named: Boolean,
    val repositories: List<String>,
    val missingGoldFiles: List<String>,
    val scores: MeasuredScores,
)

@Serializable
internal data class GroupScores(val group: String, val tasks: List<String>, val scores: MeasuredScores)

@Serializable
internal data class WorkspaceTasksResult(
    val repositories: List<String>,
    val budgets: List<Int>,
    val groups: List<GroupScores>,
    val tasks: List<TaskScores>,
)
