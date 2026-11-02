package sample.tasktide.data.db

import kotlinx.coroutines.test.runTest
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.TaskId
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskMigrationTest {
    private val directory = createTempDirectory("task-migration").toFile()
    private val databaseFile = File(directory, "tasks.db")

    private fun createInstalledDatabase() {
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.path}").use { connection ->
            connection.createStatement().use { statement ->
                INSTALLED_SCHEMA.forEach(statement::execute)
            }
        }
    }

    private fun openRepository() = TaskRepository(createTaskDatabase(DriverFactory(databaseFile)))

    @Test
    fun existingTasksSurviveTheUpgrade() = runTest {
        createInstalledDatabase()

        val upgraded = openRepository().find(TaskId("legacy"))

        assertEquals("Old task", upgraded?.title)
        assertEquals("Old notes", upgraded?.notes)
        assertEquals(Priority.HIGH, upgraded?.priority)
        assertEquals(5_000L, upgraded?.dueAt)
        assertEquals(listOf("home"), upgraded?.tags)
        assertNull(upgraded?.remindAt)
    }

    @Test
    fun upgradedDatabaseStoresReminders() = runTest {
        createInstalledDatabase()
        val repository = openRepository()

        repository.find(TaskId("legacy"))?.let { repository.upsert(it.copy(remindAt = 77L)) }

        assertEquals(77L, openRepository().find(TaskId("legacy"))?.remindAt)
    }

    @Test
    fun newDatabaseStoresRemindersAcrossRestarts() = runTest {
        openRepository().upsert(
            sample.tasktide.core.model.Task(TaskId("fresh"), "Fresh", remindAt = 99L, updatedAt = 1L),
        )

        assertEquals(99L, openRepository().find(TaskId("fresh"))?.remindAt)
    }

    private companion object {
        val INSTALLED_SCHEMA = listOf(
            "CREATE TABLE TaskRow (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                "notes TEXT NOT NULL DEFAULT '', priority INTEGER NOT NULL DEFAULT 1, due_at INTEGER, " +
                "done INTEGER NOT NULL DEFAULT 0, sync_state TEXT NOT NULL DEFAULT 'PENDING', " +
                "synced_at INTEGER, updated_at INTEGER NOT NULL)",
            "CREATE INDEX task_priority_idx ON TaskRow(priority)",
            "CREATE TABLE TagRow (task_id TEXT NOT NULL REFERENCES TaskRow(id) ON DELETE CASCADE, " +
                "name TEXT NOT NULL, PRIMARY KEY (task_id, name))",
            "INSERT INTO TaskRow(id, title, notes, priority, due_at, done, sync_state, synced_at, updated_at) " +
                "VALUES ('legacy', 'Old task', 'Old notes', 2, 5000, 0, 'PENDING', NULL, 10)",
            "INSERT INTO TagRow(task_id, name) VALUES ('legacy', 'home')",
            "PRAGMA user_version = 2",
        )
    }
}
