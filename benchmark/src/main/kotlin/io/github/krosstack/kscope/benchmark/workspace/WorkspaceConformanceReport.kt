package io.github.krosstack.kscope.benchmark.workspace

import java.util.Locale

/** The result of a conformance run of the workspace links as a short Markdown table, with what is missing below. */
internal class WorkspaceConformanceReport(private val result: WorkspaceConformanceResult) {
    fun render(): String = buildString {
        appendLine("# Workspace link conformance")
        appendLine()
        appendLine("Repositories: ${result.repositories.joinToString(", ")}")
        appendLine()
        appendLine("| Class | Scan | Found | Recall | Expected evidence | Unmatched |")
        appendLine("|---|---|---|---|---|---|")
        result.classes.forEach { appendLine(row(it)) }
        appendLine()
        appendLine(if (result.passed) "Gate: met." else "Gate: missed.")
        details().forEach { appendLine(it) }
    }

    private fun row(entry: LinkClassResult): String =
        "| `${entry.name}` | ${entry.scan} | ${entry.found}/${entry.goldCount} | ${recall(entry)} | " +
            "${entry.withExpectedEvidence}/${entry.found} | ${entry.unmatched} |"

    private fun recall(entry: LinkClassResult): String = String.format(Locale.ROOT, "%.2f", entry.recall)

    private fun details(): List<String> = result.classes.flatMap { entry ->
        entry.missing.map { "- missing in `${entry.name}`: $it" } +
            entry.evidenceMismatches.map { "- evidence in `${entry.name}`: $it" }
    }
}
