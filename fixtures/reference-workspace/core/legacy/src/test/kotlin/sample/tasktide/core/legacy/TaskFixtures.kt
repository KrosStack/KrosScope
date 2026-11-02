package sample.tasktide.core.legacy

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId

object TaskFixtures {
    @JvmStatic
    @JvmOverloads
    fun task(title: String, priority: Priority, dueAt: Long? = null): Task =
        Task(id = TaskId("fixture-${slugify(title)}"), title = title, priority = priority, dueAt = dueAt, updatedAt = 1L)
}
