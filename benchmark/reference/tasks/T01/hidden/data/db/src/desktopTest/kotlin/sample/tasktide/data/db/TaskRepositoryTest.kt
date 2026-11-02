package sample.tasktide.data.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.SyncState
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskRepositoryTest {
    private val repository = TaskRepository(createTaskDatabase(DriverFactory()))

    private fun task(id: String, title: String, priority: Priority = Priority.NORMAL) =
        Task(TaskId(id), title, priority = priority, updatedAt = 10L)

    @Test
    fun upsertedTaskIsFoundWithItsTags() = runTest {
        repository.upsert(task("a", "Book flights").copy(tags = listOf("travel", "urgent")))

        val found = repository.find(TaskId("a"))

        assertEquals("Book flights", found?.title)
        assertEquals(listOf("travel", "urgent"), found?.tags)
    }

    @Test
    fun observedTasksAreOrderedByPriority() = runTest {
        repository.upsert(task("low", "Water plants", Priority.LOW))
        repository.upsert(task("high", "File taxes", Priority.HIGH))

        val titles = repository.observeAll().first().map { it.title }

        assertEquals(listOf("File taxes", "Water plants"), titles)
    }

    @Test
    fun markedTasksLeaveThePendingList() = runTest {
        repository.upsert(task("a", "Send invoice"))
        repository.upsert(task("b", "Call dentist"))

        repository.markSynced(listOf(TaskId("a")), at = 500L)

        assertEquals(listOf(TaskId("b")), repository.pending().map { it.id })
        assertEquals(SyncState.Synced(at = 500L), repository.find(TaskId("a"))?.syncState)
        assertEquals(500L, repository.lastSyncedAt())
    }

    @Test
    fun deletedTaskIsGone() = runTest {
        repository.upsert(task("a", "Old note"))

        repository.delete(TaskId("a"))

        assertNull(repository.find(TaskId("a")))
    }

    @Test
    fun openCountsAreGroupedByPriority() = runTest {
        repository.upsert(task("a", "One", Priority.HIGH))
        repository.upsert(task("b", "Two", Priority.HIGH))
        repository.upsert(task("c", "Three", Priority.LOW).copy(done = true))

        assertEquals(mapOf(Priority.HIGH to 2L), repository.openCountsByPriority())
    }

    @Test
    fun reminderComesBackUnchanged() = runTest {
        repository.upsert(task("a", "Renew passport").copy(remindAt = 1_700_000_000_000L))

        assertEquals(1_700_000_000_000L, repository.find(TaskId("a"))?.remindAt)
    }

    @Test
    fun taskWithoutReminderHasNone() = runTest {
        repository.upsert(task("a", "Renew passport"))

        assertNull(repository.find(TaskId("a"))?.remindAt)
    }

    @Test
    fun observedTasksCarryTheirReminder() = runTest {
        repository.upsert(task("a", "Renew passport").copy(remindAt = 42L))

        assertEquals(listOf(42L), repository.observeAll().first().map { it.remindAt })
    }

    @Test
    fun changedReminderReplacesTheOldOne() = runTest {
        repository.upsert(task("a", "Renew passport").copy(remindAt = 42L))
        repository.upsert(task("a", "Renew passport").copy(remindAt = null))

        assertNull(repository.find(TaskId("a"))?.remindAt)
    }
}
