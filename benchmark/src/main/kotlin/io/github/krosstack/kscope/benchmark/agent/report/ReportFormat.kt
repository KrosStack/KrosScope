package io.github.krosstack.kscope.benchmark.agent.report

import java.util.Locale

internal object ReportFormat {
    fun number(value: Double?) = value?.let { String.format(Locale.ROOT, "%,.0f", it) } ?: "-"

    fun decimal(value: Double?) = value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "-"

    fun money(value: Double?) = value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "-"

    fun percent(value: Double?) = value?.let { String.format(Locale.ROOT, "%.0f%%", it * PERCENT) } ?: "-"

    fun ratio(value: Double?) = value?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "-"

    fun runs(cell: CellStats): String {
        val base = "${cell.completed}/${cell.scheduled}"
        return if (cell.failed + cell.missing > 0) "$base (${cell.failed} failed, ${cell.missing} missing)" else base
    }

    fun success(cell: CellStats) = "${cell.successes}/${cell.completed}"

    private const val PERCENT = 100
}
