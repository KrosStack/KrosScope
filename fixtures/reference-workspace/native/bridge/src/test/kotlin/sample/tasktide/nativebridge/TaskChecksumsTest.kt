package sample.tasktide.nativebridge

import org.junit.Test
import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import kotlin.test.assertEquals

class TaskChecksumsTest {
    @Test
    fun payloadContainsIdentityAndPriorityLevel() {
        val task = Task(TaskId("abc"), "Water plants", priority = Priority.HIGH, updatedAt = 42L)

        assertEquals("abc|Water plants|2|42", task.checksumPayload().decodeToString())
    }

    @Test
    fun payloadChangesWhenTheTaskIsEdited() {
        val task = Task(TaskId("abc"), "Water plants", updatedAt = 42L)

        val edited = task.copy(title = "Water the plants")

        assertEquals(false, task.checksumPayload().contentEquals(edited.checksumPayload()))
    }
}
