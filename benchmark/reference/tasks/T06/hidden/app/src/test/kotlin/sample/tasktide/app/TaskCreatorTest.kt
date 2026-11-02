package sample.tasktide.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.app.flavor.FlavorFlags
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TaskCreatorTest {
    private val store = InMemoryTaskStore()

    private fun flags(limit: Int) = object : FeatureFlags by FlavorFlags {
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
    fun openTasksBelowTheLimitStillAllowCreation() = runTest {
        store.upsert(Task(TaskId("a"), "First", updatedAt = 1L))

        val result = TaskCreator(store, flags(limit = 2)).create("Second")

        assertIs<CreateResult.Created>(result)
    }
}
