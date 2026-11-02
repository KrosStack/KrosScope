package sample.tasktide.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskBundleTest {
    @Test
    fun bundleSurvivesJsonRoundTrip() {
        val tasks = listOf(
            Task(TaskId("a"), "Plan sprint", priority = Priority.HIGH, dueAt = 5L, tags = listOf("work"), updatedAt = 1L),
            Task(TaskId("b"), "Buy milk", done = true, syncState = SyncState.Synced(at = 9L), updatedAt = 2L),
        )

        val decoded = TaskBundle.decode(TaskBundle(source = "test", exportedAt = 10L, tasks = tasks).encode())

        assertEquals(tasks, decoded.tasks)
        assertEquals("test", decoded.source)
    }
}
