package sample.tasktide.core.legacy

import sample.tasktide.core.model.Priority

object PriorityLabels {
    const val UNKNOWN = "unknown"

    fun labelFor(priority: Priority): String = when (priority) {
        Priority.LOW -> "low"
        Priority.NORMAL -> "normal"
        Priority.HIGH -> "high"
        Priority.URGENT -> "urgent"
    }

    fun parse(label: String): Priority? =
        Priority.entries.firstOrNull { labelFor(it) == label.trim().lowercase() }
}
