package sample.tasktide.sync.engine

import sample.tasktide.core.model.Priority
import sample.tasktide.core.model.Task
import sample.tasktide.core.model.TaskId
import sample.tasktide.sync.proto.PriorityDto
import sample.tasktide.sync.proto.TaskDto

object TaskDtoMapper {
    fun toDto(task: Task): TaskDto = TaskDto(
        id = task.id.value,
        title = task.title,
        notes = task.notes,
        priority = toDto(task.priority),
        due_at = task.dueAt ?: 0L,
        done = task.done,
        tags = task.tags,
        updated_at = task.updatedAt,
    )

    fun fromDto(dto: TaskDto): Task = Task(
        id = TaskId(dto.id),
        title = dto.title,
        notes = dto.notes,
        priority = fromDto(dto.priority),
        dueAt = dto.due_at.takeIf { it != 0L },
        done = dto.done,
        tags = dto.tags,
        updatedAt = dto.updated_at,
    )

    private fun toDto(priority: Priority): PriorityDto = when (priority) {
        Priority.LOW -> PriorityDto.PRIORITY_LOW
        Priority.NORMAL -> PriorityDto.PRIORITY_NORMAL
        Priority.HIGH -> PriorityDto.PRIORITY_HIGH
        Priority.URGENT -> PriorityDto.PRIORITY_URGENT
    }

    private fun fromDto(priority: PriorityDto): Priority = when (priority) {
        PriorityDto.PRIORITY_UNSPECIFIED -> Priority.NORMAL
        PriorityDto.PRIORITY_LOW -> Priority.LOW
        PriorityDto.PRIORITY_NORMAL -> Priority.NORMAL
        PriorityDto.PRIORITY_HIGH -> Priority.HIGH
        PriorityDto.PRIORITY_URGENT -> Priority.URGENT
    }
}
