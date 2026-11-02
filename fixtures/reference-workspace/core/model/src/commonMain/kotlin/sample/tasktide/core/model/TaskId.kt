package sample.tasktide.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "Task id must not be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun generate(generator: UuidGenerator = UuidGenerator()): TaskId = TaskId(generator.next())
    }
}
