package sample.tasktide.data.db

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.SyncState
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId

object TaskRowMapper {
    fun toTask(row: TaskRow, tags: List<String>): Task = Task(
        id = TaskId(row.id),
        title = row.title,
        notes = row.notes,
        priority = Priority.fromLevel(row.priority.toInt()),
        dueAt = row.due_at,
        done = row.done != 0L,
        tags = tags,
        syncState = syncStateOf(row.sync_state, row.synced_at),
        updatedAt = row.updated_at,
    )

    fun toRow(task: Task): TaskRow = TaskRow(
        id = task.id.value,
        title = task.title,
        notes = task.notes,
        priority = task.priority.level.toLong(),
        due_at = task.dueAt,
        done = if (task.done) 1L else 0L,
        sync_state = stateName(task.syncState),
        synced_at = (task.syncState as? SyncState.Synced)?.at,
        updated_at = task.updatedAt,
    )

    private fun syncStateOf(name: String, syncedAt: Long?): SyncState = when (name) {
        "SYNCED" -> SyncState.Synced(syncedAt ?: 0L)
        "CONFLICT" -> SyncState.Conflict
        else -> SyncState.Pending
    }

    private fun stateName(state: SyncState): String = when (state) {
        is SyncState.Pending -> "PENDING"
        is SyncState.Synced -> "SYNCED"
        is SyncState.Conflict -> "CONFLICT"
    }
}
