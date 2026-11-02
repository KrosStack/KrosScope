package sample.tasktide.sync.engine

import kotlinx.coroutines.test.runTest
import sample.tasktide.core.model.SyncState
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.data.db.DriverFactory
import sample.tasktide.data.db.TaskRepository
import sample.tasktide.data.db.createTaskDatabase
import sample.tasktide.sync.proto.PriorityDto
import sample.tasktide.sync.proto.TaskDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncEngineTest {
    private val repository = TaskRepository(createTaskDatabase(DriverFactory()))

    private fun engine(transport: SyncTransport) = SyncEngine(repository, transport, deviceId = "test-device")

    @Test
    fun pendingTasksArePushedAndRemoteTasksPulled() = runTest {
        repository.upsert(Task(TaskId("local"), "Local task", updatedAt = 10L))
        val remote = TaskDto(id = "remote", title = "Remote task", priority = PriorityDto.PRIORITY_HIGH, updated_at = 20L)
        val transport = FakeSyncTransport(seed = listOf(remote), clock = { 1_000L })

        val result = engine(transport).sync()

        assertEquals(SyncResult.Success(pushed = 1, pulled = 1), result)
        assertEquals("Local task", transport.stored("local")?.title)
        assertEquals(SyncState.Synced(at = 1_000L), repository.find(TaskId("remote"))?.syncState)
        assertEquals(SyncState.Synced(at = 1_000L), repository.find(TaskId("local"))?.syncState)
    }

    @Test
    fun transportFailureLeavesTasksPending() = runTest {
        repository.upsert(Task(TaskId("local"), "Local task", updatedAt = 10L))
        val transport = FakeSyncTransport().apply { failNext = true }

        val result = engine(transport).sync()

        assertIs<SyncResult.Failed>(result)
        assertEquals(listOf(TaskId("local")), repository.pending().map { it.id })
    }
}
