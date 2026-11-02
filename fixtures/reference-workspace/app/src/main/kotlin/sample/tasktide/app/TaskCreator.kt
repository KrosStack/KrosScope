package sample.tasktide.app

import kotlinx.coroutines.flow.first
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore

class TaskCreator(
    private val store: TaskStore,
    private val flags: FeatureFlags,
) {
    suspend fun create(title: String, priority: Priority = Priority.NORMAL): CreateResult {
        val open = store.observeAll().first().count { !it.done }
        if (open >= flags.maxTasks) return CreateResult.LimitReached(flags.maxTasks)
        val task = Task(id = TaskId.generate(), title = title.trim(), priority = priority)
        store.upsert(task)
        return CreateResult.Created(task)
    }
}

sealed interface CreateResult {
    data class Created(val task: Task) : CreateResult

    data class LimitReached(val max: Int) : CreateResult
}
