package sample.tasktide.data.db

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore

class TaskRepository(private val database: TaskDatabase) : TaskStore {
    private val taskQueries = database.taskQueries
    private val tagQueries = database.tagQueries

    override fun observeAll(): Flow<List<Task>> = combine(
        taskQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
        tagQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
    ) { rows, tagRows ->
        val tagsByTask = tagRows.groupBy({ it.task_id }, { it.name })
        rows.map { TaskRowMapper.toTask(it, tagsByTask[it.id].orEmpty()) }
    }

    override suspend fun find(id: TaskId): Task? = withContext(Dispatchers.Default) {
        taskQueries.selectById(id.value).executeAsOneOrNull()?.let { row ->
            TaskRowMapper.toTask(row, tagQueries.selectForTask(row.id).executeAsList())
        }
    }

    override suspend fun upsert(task: Task) {
        withContext(Dispatchers.Default) {
            database.transaction {
                taskQueries.upsert(TaskRowMapper.toRow(task))
                tagQueries.deleteForTask(task.id.value)
                task.tags.forEach { tagQueries.insert(task.id.value, it) }
            }
        }
    }

    override suspend fun delete(id: TaskId) {
        withContext(Dispatchers.Default) {
            database.transaction {
                tagQueries.deleteForTask(id.value)
                taskQueries.deleteById(id.value)
            }
        }
    }

    suspend fun pending(): List<Task> = withContext(Dispatchers.Default) {
        taskQueries.selectPending().executeAsList().map { row ->
            TaskRowMapper.toTask(row, tagQueries.selectForTask(row.id).executeAsList())
        }
    }

    suspend fun markSynced(ids: List<TaskId>, at: Long) {
        withContext(Dispatchers.Default) {
            database.transaction {
                ids.forEach { taskQueries.markSynced(at, it.value) }
            }
        }
    }

    suspend fun lastSyncedAt(): Long = withContext(Dispatchers.Default) {
        taskQueries.lastSyncedAt().executeAsOne().last_synced ?: 0L
    }

    suspend fun openCountsByPriority(): Map<Priority, Long> = withContext(Dispatchers.Default) {
        taskQueries.countOpenByPriority().executeAsList()
            .associate { Priority.fromLevel(it.priority.toInt()) to it.open_count }
    }
}
