package sample.tasktide.core.model

import kotlinx.coroutines.flow.Flow

interface TaskStore {
    fun observeAll(): Flow<List<Task>>

    suspend fun find(id: TaskId): Task?

    suspend fun upsert(task: Task)

    suspend fun delete(id: TaskId)
}
