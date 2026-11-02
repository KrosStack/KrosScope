package io.github.krosstack.kscope.benchmark.agent.report

import io.github.krosstack.kscope.benchmark.agent.AgentTaskSpec
import io.github.krosstack.kscope.benchmark.agent.Arm
import io.github.krosstack.kscope.benchmark.agent.ContextRecord
import io.github.krosstack.kscope.benchmark.agent.RunBinding
import io.github.krosstack.kscope.benchmark.agent.RunRecord
import io.github.krosstack.kscope.benchmark.agent.RunStatus
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln

@Serializable
internal data class CellStats(
    val arm: Arm,
    val scheduled: Int,
    val completed: Int,
    val failed: Int,
    val missing: Int,
    val successes: Int,
    val passRate: Double?,
    val meanTotalTokens: Double?,
    val meanUncachedTokens: Double?,
    val meanCostUsd: Double?,
    val meanTurns: Double?,
    val meanToolCalls: Double?,
    val meanWallSeconds: Double?,
    val meanContextTokens: Double?,
    val meanGoldRecall: Double?,
    val meanContextRecall: Double?,
    val meanContextPrecision: Double?,
    val meanUnnecessaryFiles: Double?,
    val meanFirstGoldReadEvent: Double?,
    val meanFirstGoldEditEvent: Double?,
    val cacheHitRatio: Double?,
)

@Serializable
internal data class TaskStats(val taskId: String, val title: String, val cells: List<CellStats>)

@Serializable
internal data class ArmStats(
    val arm: Arm,
    val cell: CellStats,
    val tokensPerSolvedTask: Double?,
    val costPerSolvedTask: Double?,
    val tokenRatioToA: Double?,
    val costRatioToA: Double?,
)

@Serializable
internal data class RunIssue(val taskId: String, val arm: Arm, val repeat: Int, val reason: String)

@Serializable
internal data class AggregateReport(
    val schemaVersion: Int = 1,
    val agent: String,
    val model: String?,
    val dryRun: Boolean,
    val budget: Int,
    val estimator: String,
    val promptVersion: String,
    val tasks: List<TaskStats>,
    val arms: List<ArmStats>,
    val failedRuns: List<RunIssue>,
    val missingRuns: List<RunIssue>,
    val warningRuns: List<RunIssue>,
)

internal class ReportInput(
    val tasks: List<AgentTaskSpec>,
    val arms: List<Arm>,
    val repeats: Int,
    val records: List<RunRecord>,
    val binding: RunBinding,
)

internal object AgentReportBuilder {
    fun build(input: ReportInput): AggregateReport {
        val perTask = input.tasks.map { spec ->
            TaskStats(spec.id, spec.title, input.arms.map { arm -> cell(spec, arm, input) })
        }
        val failed = input.records.filter { it.status == RunStatus.FAILED }.sortedBy { it.key }.map {
            RunIssue(it.taskId, it.arm, it.repeat, it.failure?.code?.label ?: "unknown")
        }
        val warned = input.records.filter { it.telemetry.warnings.isNotEmpty() }.sortedBy { it.key }.map {
            RunIssue(it.taskId, it.arm, it.repeat, it.telemetry.warnings.joinToString("; "))
        }
        return AggregateReport(
            agent = input.binding.agent,
            model = input.binding.model,
            dryRun = input.binding.dryRun,
            budget = input.binding.budget,
            estimator = input.binding.estimator,
            promptVersion = input.binding.promptVersion,
            tasks = perTask,
            arms = armStats(input.arms, perTask),
            failedRuns = failed,
            missingRuns = missing(input),
            warningRuns = warned,
        )
    }

    private fun missing(input: ReportInput): List<RunIssue> {
        val known = input.records.map { it.key }.toSet()
        return input.tasks.flatMap { spec ->
            input.arms.flatMap { arm ->
                (1..input.repeats).filter { RunRecord.runKey(spec.id, arm, it) !in known }
                    .map { RunIssue(spec.id, arm, it, "not run") }
            }
        }
    }

    private fun cell(spec: AgentTaskSpec, arm: Arm, input: ReportInput): CellStats {
        val records = input.records.filter { it.taskId == spec.id && it.arm == arm && it.repeat <= input.repeats }
        val done = records.filter { it.status == RunStatus.COMPLETED }
        val goldCount = spec.goldFiles.size.coerceAtLeast(1).toDouble()
        val telemetry = done.map { it.telemetry }
        val withContext = done.filter { it.context.files.isNotEmpty() }
        val retrievals = if (arm.usesContext) done else emptyList()
        val cache = telemetry.sumOf { it.cacheReadTokens }.toDouble()
        val prompt = telemetry.sumOf { it.cacheReadTokens + it.cacheCreationTokens + it.inputTokens }.toDouble()
        return CellStats(
            arm = arm,
            scheduled = input.repeats,
            completed = done.size,
            failed = records.size - done.size,
            missing = (input.repeats - records.size).coerceAtLeast(0),
            successes = done.count { it.verification?.passed == true },
            passRate = ratio(done.count { it.verification?.passed == true }.toDouble(), done.size.toDouble()),
            meanTotalTokens = mean(telemetry.map { it.totalTokens.toDouble() }),
            meanUncachedTokens = mean(telemetry.map { it.uncachedTokens.toDouble() }),
            meanCostUsd = mean(telemetry.mapNotNull { it.costUsd }),
            meanTurns = mean(telemetry.map { it.turns.toDouble() }),
            meanToolCalls = mean(telemetry.map { it.toolCallTotal.toDouble() }),
            meanWallSeconds = mean(telemetry.map { it.wallMs / MILLIS }),
            meanContextTokens = mean(done.map { it.context.tokens.toDouble() }),
            meanGoldRecall = mean(done.mapNotNull { it.gold }.map { it.goldFilesHit.size / goldCount }),
            meanContextRecall = mean(retrievals.map { it.context.goldFilesInContext / goldCount }),
            meanContextPrecision = mean(withContext.map { precision(it.context) }),
            meanUnnecessaryFiles = mean(done.mapNotNull { it.gold?.unnecessaryFiles?.size?.toDouble() }),
            meanFirstGoldReadEvent = mean(telemetry.mapNotNull { it.firstGoldRead?.eventIndex?.toDouble() }),
            meanFirstGoldEditEvent = mean(telemetry.mapNotNull { it.firstGoldEdit?.eventIndex?.toDouble() }),
            cacheHitRatio = ratio(cache, prompt),
        )
    }

    private fun armStats(arms: List<Arm>, tasks: List<TaskStats>): List<ArmStats> = arms.map { arm ->
        val cells = tasks.mapNotNull { task -> task.cells.firstOrNull { it.arm == arm } }
        val total = totals(cells, arm)
        ArmStats(
            arm = arm,
            cell = total,
            tokensPerSolvedTask = solvedNormalized(cells) { it.meanTotalTokens },
            costPerSolvedTask = solvedNormalized(cells) { it.meanCostUsd },
            tokenRatioToA = pairedRatio(tasks, arm) { it.meanTotalTokens },
            costRatioToA = pairedRatio(tasks, arm) { it.meanCostUsd },
        )
    }

    private fun totals(cells: List<CellStats>, arm: Arm): CellStats {
        val completed = cells.sumOf { it.completed }
        fun weighted(value: (CellStats) -> Double?): Double? = weightedMean(cells, value)
        return CellStats(
            arm = arm,
            scheduled = cells.sumOf { it.scheduled },
            completed = completed,
            failed = cells.sumOf { it.failed },
            missing = cells.sumOf { it.missing },
            successes = cells.sumOf { it.successes },
            passRate = ratio(cells.sumOf { it.successes }.toDouble(), completed.toDouble()),
            meanTotalTokens = weighted { it.meanTotalTokens },
            meanUncachedTokens = weighted { it.meanUncachedTokens },
            meanCostUsd = weighted { it.meanCostUsd },
            meanTurns = weighted { it.meanTurns },
            meanToolCalls = weighted { it.meanToolCalls },
            meanWallSeconds = weighted { it.meanWallSeconds },
            meanContextTokens = weighted { it.meanContextTokens },
            meanGoldRecall = weighted { it.meanGoldRecall },
            meanContextRecall = weighted { it.meanContextRecall },
            meanContextPrecision = weighted { it.meanContextPrecision },
            meanUnnecessaryFiles = weighted { it.meanUnnecessaryFiles },
            meanFirstGoldReadEvent = null,
            meanFirstGoldEditEvent = null,
            cacheHitRatio = weighted { it.cacheHitRatio },
        )
    }

    private fun solvedNormalized(cells: List<CellStats>, value: (CellStats) -> Double?): Double? {
        val measured = cells.filter { value(it) != null }
        val spent = measured.sumOf { (value(it) ?: 0.0) * it.completed }
        return ratio(spent, measured.sumOf { it.successes }.toDouble())
    }

    private fun weightedMean(cells: List<CellStats>, value: (CellStats) -> Double?): Double? {
        val measured = cells.filter { value(it) != null }
        return ratio(measured.sumOf { (value(it) ?: 0.0) * it.completed }, measured.sumOf { it.completed }.toDouble())
    }

    private fun pairedRatio(tasks: List<TaskStats>, arm: Arm, value: (CellStats) -> Double?): Double? {
        if (arm == Arm.A) return null
        val logs = tasks.mapNotNull { task ->
            val base = task.cells.firstOrNull { it.arm == Arm.A }?.let(value)
            val other = task.cells.firstOrNull { it.arm == arm }?.let(value)
            val ratio = if (base != null && other != null) other / base else null
            ratio?.takeIf { it > 0 && it.isFinite() }?.let(::ln)
        }
        return if (logs.isEmpty()) null else exp(logs.average())
    }

    private fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.average()

    private fun ratio(numerator: Double, denominator: Double): Double? =
        if (denominator > 0) numerator / denominator else null

    private fun precision(context: ContextRecord): Double = context.goldFilesInContext.toDouble() / context.files.size

    private const val MILLIS = 1000.0
}
