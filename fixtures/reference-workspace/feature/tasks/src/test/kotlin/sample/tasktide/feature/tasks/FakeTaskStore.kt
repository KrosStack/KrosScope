package sample.tasktide.feature.tasks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore

class FakeTaskStore : TaskStore {
    private val tasks = MutableStateFlow<Map<TaskId, Task>>(emptyMap())

    override fun observeAll(): Flow<List<Task>> = tasks.map { it.values.toList() }

    override suspend fun find(id: TaskId): Task? = tasks.value[id]

    override suspend fun upsert(task: Task) {
        tasks.value = tasks.value + (task.id to task)
    }

    override suspend fun delete(id: TaskId) {
        tasks.value = tasks.value - id
    }
}
