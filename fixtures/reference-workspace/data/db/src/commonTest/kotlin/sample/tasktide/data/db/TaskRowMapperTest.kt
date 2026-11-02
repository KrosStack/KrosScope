package sample.tasktide.data.db

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.SyncState
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskRowMapperTest {
    @Test
    fun priorityIsStoredAsLevelAndRestored() {
        val task = Task(TaskId("1"), "Review PR", priority = Priority.URGENT, updatedAt = 5L)

        val row = TaskRowMapper.toRow(task)

        assertEquals(3L, row.priority)
        assertEquals(Priority.URGENT, TaskRowMapper.toTask(row, emptyList()).priority)
    }

    @Test
    fun syncedStateKeepsItsTimestamp() {
        val task = Task(TaskId("2"), "Deploy", syncState = SyncState.Synced(at = 77L), updatedAt = 5L)

        val restored = TaskRowMapper.toTask(TaskRowMapper.toRow(task), emptyList())

        assertEquals(SyncState.Synced(at = 77L), restored.syncState)
    }

    @Test
    fun tagsAreAttachedByTheCaller() {
        val row = TaskRowMapper.toRow(Task(TaskId("3"), "Plan trip", updatedAt = 1L))

        assertEquals(listOf("travel"), TaskRowMapper.toTask(row, listOf("travel")).tags)
    }
}
