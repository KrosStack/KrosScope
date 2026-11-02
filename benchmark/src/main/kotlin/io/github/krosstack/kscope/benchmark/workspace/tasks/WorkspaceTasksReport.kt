package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkMethod
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import java.util.Locale

/** Renders the result of the level 4 tasks as Markdown; every figure cell reads KrosScope / BM25 English / BM25 raw. */
internal class WorkspaceTasksReport(private val result: WorkspaceTasksResult) {
    private val lines: List<Line> = result.groups.map { Line(it.group, it.tasks.size, it.scores) } +
        result.tasks.map { Line(it.id, 1, it.scores) }

    fun render(): String = buildString {
        appendLine("# Level 4 tasks on the workspace fixture")
        appendLine()
        appendLine("${result.tasks.size} tasks over ${result.repositories.size} repositories. The figures describe")
        appendLine("these tasks only and carry no gate. Each cell is KrosScope / BM25 English / BM25 raw.")
        appendTasks()
        appendLocalization()
        appendContext("Gold recall of a context package", ContextRow::goldRecall)
        appendContext("Token precision of a context package", ContextRow::tokenPrecision)
    }

    private fun StringBuilder.appendTasks() {
        appendLine()
        appendLine("## Tasks")
        appendLine()
        appendLine("| Task | Named | Edited repositories | Gold files not indexed | Title |")
        appendLine("|---|---|---|---|---|")
        result.tasks.forEach {
            val named = if (it.named) "yes" else "no"
            val missing = it.missingGoldFiles.joinToString().ifEmpty { "none" }
            appendLine("| ${it.id} | $named | ${it.repositories.joinToString()} | $missing | ${it.title} |")
        }
    }

    private fun StringBuilder.appendLocalization() {
        appendLine()
        appendLine("## Localization")
        appendLine()
        appendLine("| Tasks | Set | Recall@5 | Recall@10 | NDCG@10 |")
        appendLine("|---:|---|---|---|---|")
        lines.forEach { line ->
            val rows = LOCALIZATION_METHODS.map { method -> line.scores.localization.single { it.method == method } }
            val cells = listOf(LocalizationRow::recall5, LocalizationRow::recall10, LocalizationRow::ndcg10)
                .joinToString(" | ") { metric -> triple(rows.map(metric)) }
            appendLine("| ${line.tasks} | ${line.label} | $cells |")
        }
    }

    private fun StringBuilder.appendContext(title: String, metric: (ContextRow) -> Double) {
        appendLine()
        appendLine("## $title")
        appendLine()
        appendLine("| Tasks | Set | ${result.budgets.joinToString(" | ") { "%,d tokens".format(Locale.ROOT, it) }} |")
        appendLine("|---:|---|${"---|".repeat(result.budgets.size)}")
        lines.forEach { line ->
            val cells = result.budgets.joinToString(" | ") { budget ->
                val rows = line.scores.context.filter { it.budget == budget }
                triple(CONTEXT_METHODS.map { method -> metric(rows.single { it.method == method }) })
            }
            appendLine("| ${line.tasks} | ${line.label} | $cells |")
        }
    }

    private fun triple(values: List<Double>): String =
        values.joinToString(" / ") { String.format(Locale.ROOT, "%.4f", it) }

    private class Line(val label: String, val tasks: Int, val scores: MeasuredScores)

    private companion object {
        val LOCALIZATION_METHODS = listOf(BenchmarkMethod.FULL, BenchmarkMethod.BM25_ENGLISH, BenchmarkMethod.BM25_RAW)
        val CONTEXT_METHODS = listOf(
            ContextBenchmarkMethod.FULL_COMPILER,
            ContextBenchmarkMethod.BM25_ENGLISH,
            ContextBenchmarkMethod.BM25_RAW,
        )
    }
}
