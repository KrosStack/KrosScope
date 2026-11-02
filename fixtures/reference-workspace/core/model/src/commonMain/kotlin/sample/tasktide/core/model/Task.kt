package sample.tasktide.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: TaskId,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.NORMAL,
    val dueAt: Long? = null,
    val done: Boolean = false,
    val tags: List<String> = emptyList(),
    val syncState: SyncState = SyncState.Pending,
    val updatedAt: Long = currentTimeMillis(),
) {
    fun toggled(now: Long = currentTimeMillis()): Task =
        copy(done = !done, syncState = SyncState.Pending, updatedAt = now)
}
