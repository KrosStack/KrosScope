package sample.tasktide.app

import kotlinx.coroutines.test.runTest
import org.junit.Test
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.app.flavor.FlavorFlags
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FullTaskLimitTest {
    private val store = InMemoryTaskStore()

    private fun flags(limit: Int) = object : FeatureFlags by FlavorFlags {
        override val maxTasks: Int = limit
    }

    @Test
    fun completedTasksDoNotCountTowardsTheLimit() = runTest {
        store.upsert(Task(TaskId("done"), "Done already", done = true, updatedAt = 1L))

        val result = TaskCreator(store, flags(limit = 1)).create("Next")

        assertIs<CreateResult.Created>(result)
    }

    @Test
    fun onlyOpenTasksCountTowardsTheLimit() = runTest {
        repeat(3) { store.upsert(Task(TaskId("done-$it"), "Done $it", done = true, updatedAt = 1L)) }
        store.upsert(Task(TaskId("open"), "Open", updatedAt = 1L))

        val result = TaskCreator(store, flags(limit = 1)).create("Next")

        assertEquals(CreateResult.LimitReached(max = 1), result)
    }

    @Test
    fun fullEditionKeepsSyncEnabled() {
        assertEquals(true, FlavorFlags.syncEnabled)
    }
}
