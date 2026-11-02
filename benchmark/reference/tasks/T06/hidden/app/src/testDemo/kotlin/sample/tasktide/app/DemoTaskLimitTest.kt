package sample.tasktide.app

import kotlinx.coroutines.test.runTest
import org.junit.Test
import sample.tasktide.app.config.FeatureFlags
import sample.tasktide.app.flavor.FlavorFlags
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DemoTaskLimitTest {
    private val store = InMemoryTaskStore()

    private fun flags(limit: Int) = object : FeatureFlags by FlavorFlags {
        override val maxTasks: Int = limit
    }

    private suspend fun addTasks(open: Int, done: Int) {
        repeat(open) { store.upsert(Task(TaskId("open-$it"), "Open $it", updatedAt = 1L)) }
        repeat(done) { store.upsert(Task(TaskId("done-$it"), "Done $it", done = true, updatedAt = 1L)) }
    }

    @Test
    fun completedTasksCountTowardsTheLimit() = runTest {
        addTasks(open = 0, done = 1)

        val result = TaskCreator(store, flags(limit = 1)).create("Next")

        assertEquals(CreateResult.LimitReached(max = 1), result)
    }

    @Test
    fun openAndCompletedTasksAreAddedUp() = runTest {
        addTasks(open = 10, done = 15)

        val result = TaskCreator(store, FlavorFlags).create("Next")

        assertEquals(CreateResult.LimitReached(max = FlavorFlags.maxTasks), result)
    }

    @Test
    fun demoLimitStillAllowsTasksBelowIt() = runTest {
        addTasks(open = 12, done = 12)

        val result = TaskCreator(store, FlavorFlags).create("Next")

        assertIs<CreateResult.Created>(result)
    }

    @Test
    fun demoEditionKeepsSyncDisabled() {
        assertEquals(false, FlavorFlags.syncEnabled)
    }
}
