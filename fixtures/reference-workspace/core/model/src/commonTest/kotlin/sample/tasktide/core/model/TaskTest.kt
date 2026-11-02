package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TaskTest {
    private val task = Task(id = TaskId("t-1"), title = "Write report", updatedAt = 100L)

    @Test
    fun newTaskIsPendingAndOpen() {
        assertFalse(task.done)
        assertEquals(SyncState.Pending, task.syncState)
        assertEquals(Priority.NORMAL, task.priority)
    }

    @Test
    fun toggledFlipsDoneAndRequeuesForSync() {
        val synced = task.copy(syncState = SyncState.Synced(at = 90L))

        val toggled = synced.toggled(now = 200L)

        assertTrue(toggled.done)
        assertEquals(SyncState.Pending, toggled.syncState)
        assertEquals(200L, toggled.updatedAt)
    }

    @Test
    fun blankIdIsRejected() {
        assertFailsWith<IllegalArgumentException> { TaskId(" ") }
    }
}
