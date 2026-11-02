package sample.tasktide.feature.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.SyncState
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val store = FakeTaskStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun itemsCarryPriorityLabelAndDueDate() = runTest(dispatcher) {
        store.upsert(Task(TaskId("1"), "Pay rent", priority = Priority.HIGH, dueAt = 0L, updatedAt = 1L))
        val viewModel = TaskListViewModel(store)
        backgroundScope.launch(dispatcher) { viewModel.state.collect() }

        val item = viewModel.state.value.items.single()

        assertEquals("high", item.priorityLabel)
        assertEquals("1970-01-01", item.dueDate)
    }

    @Test
    fun toggleDoneMarksTheTaskAndRequeuesIt() = runTest(dispatcher) {
        store.upsert(Task(TaskId("1"), "Pay rent", syncState = SyncState.Synced(at = 5L), updatedAt = 1L))
        val viewModel = TaskListViewModel(store)

        viewModel.toggleDone(TaskId("1"))

        val stored = store.find(TaskId("1"))
        assertTrue(stored!!.done)
        assertEquals(SyncState.Pending, stored.syncState)
    }

    @Test
    fun clearCompletedRemovesOnlyFinishedTasks() = runTest(dispatcher) {
        store.upsert(Task(TaskId("open"), "Open", updatedAt = 1L))
        store.upsert(Task(TaskId("done"), "Done", done = true, updatedAt = 1L))
        val viewModel = TaskListViewModel(store)

        viewModel.clearCompleted()

        assertNull(store.find(TaskId("done")))
        assertEquals("Open", store.find(TaskId("open"))?.title)
    }
}
