package sample.tasktide.core.legacy

import sample.tasktide.core.model.Task

class CsvExporter(private val formatter: LegacyTaskFormatter = LegacyTaskFormatter(",")) : Exporter {
    override fun export(tasks: List<Task>): String =
        tasks.joinToString(separator = "\n", prefix = "$HEADER\n") { row(it) }

    private fun row(task: Task): String {
        val due = DateUtils.formatIso(task.dueAt ?: 0L)
        return listOf(task.title.quoted(), formatter.priorityText(task), due, formatter.slug(task))
            .joinToString(formatter.separator)
    }

    private fun String.quoted(): String =
        if (any { it == ',' || it == '"' }) "\"${replace("\"", "\"\"")}\"" else this

    private companion object {
        const val HEADER = "title,priority,due,slug"
    }
}
