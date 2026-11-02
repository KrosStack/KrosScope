package sample.tasktide.feature.tasks

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.TaskId

data class TaskItem(
    val id: TaskId,
    val title: String,
    val priority: Priority,
    val priorityLabel: String,
    val dueDate: String?,
    val done: Boolean,
)

data class TaskListUiState(
    val items: List<TaskItem> = emptyList(),
    val loading: Boolean = true,
)
