package sample.tasktide.core.legacy

import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskBundle
import sample.tasktide.core.model.currentTimeMillis

object ExportRegistry {
    private val exporters: Map<String, Exporter> = mapOf(
        "csv" to CsvExporter(),
        "json" to Exporter { tasks -> TaskBundle.of(tasks).encode() },
        "overdue" to Exporter { tasks -> tasks.filter { isOverdue(it) }.joinToString("\n") { it.title } },
    )

    fun formats(): Set<String> = exporters.keys

    fun find(format: String): Exporter? = exporters[format]

    private fun isOverdue(task: Task): Boolean {
        val dueAt = task.dueAt ?: return false
        return !task.done && DateUtils.isOverdue(dueAt, currentTimeMillis())
    }
}
