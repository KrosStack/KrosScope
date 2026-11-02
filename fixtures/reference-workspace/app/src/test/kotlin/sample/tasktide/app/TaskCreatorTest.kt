package sample.tasktide.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.core.model.TaskStore
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TaskCreatorTest {
    private val store = InMemoryTaskStore()

    private fun flags(limit: Int) = object : FeatureFlags {
        override val syncEnabled: Boolean = true
        override val maxTasks: Int = limit
    }

    @Test
    fun createdTaskIsTrimmedAndStored() = runTest {
        val result = TaskCreator(store, flags(limit = 5)).create("  Buy stamps ", Priority.HIGH)

        assertIs<CreateResult.Created>(result)
        val stored = store.observeAll().first().single()
        assertEquals("Buy stamps", stored.title)
        assertEquals(Priority.HIGH, stored.priority)
    }

    @Test
    fun creationStopsAtTheOpenTaskLimit() = runTest {
        store.upsert(Task(TaskId("a"), "First", updatedAt = 1L))
        val creator = TaskCreator(store, flags(limit = 1))

        val result = creator.create("Second")

        assertEquals(CreateResult.LimitReached(max = 1), result)
        assertEquals(1, store.observeAll().first().size)
    }

    @Test
    fun completedTasksDoNotCountTowardsTheLimit() = runTest {
        store.upsert(Task(TaskId("a"), "Done already", done = true, updatedAt = 1L))

        val result = TaskCreator(store, flags(limit = 1)).create("Next")

        assertIs<CreateResult.Created>(result)
    }

    private class InMemoryTaskStore : TaskStore {
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
}
