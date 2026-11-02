package sample.tasktide.sync.engine

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.sync.proto.PriorityDto
import sample.tasktide.sync.proto.TaskDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskDtoMapperTest {
    @Test
    fun everyPriorityRoundTrips() {
        Priority.entries.forEach { priority ->
            val task = Task(TaskId("p-${priority.level}"), "Task", priority = priority, updatedAt = 1L)

            assertEquals(priority, TaskDtoMapper.fromDto(TaskDtoMapper.toDto(task)).priority)
        }
    }

    @Test
    fun missingDueDateIsEncodedAsZeroAndDecodedAsNull() {
        val task = Task(TaskId("a"), "No due", updatedAt = 1L)

        val dto = TaskDtoMapper.toDto(task)

        assertEquals(0L, dto.due_at)
        assertNull(TaskDtoMapper.fromDto(dto).dueAt)
    }

    @Test
    fun unspecifiedPriorityBecomesNormal() {
        val dto = TaskDto(id = "x", title = "From server", priority = PriorityDto.PRIORITY_UNSPECIFIED)

        assertEquals(Priority.NORMAL, TaskDtoMapper.fromDto(dto).priority)
    }

    @Test
    fun tagsAndDoneFlagSurvive() {
        val task = Task(TaskId("t"), "Tagged", done = true, tags = listOf("home", "chores"), updatedAt = 3L)

        val restored = TaskDtoMapper.fromDto(TaskDtoMapper.toDto(task))

        assertEquals(listOf("home", "chores"), restored.tags)
        assertEquals(true, restored.done)
    }
}
