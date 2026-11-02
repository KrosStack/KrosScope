package sample.tasktide.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TaskBundle(
    val source: String,
    val exportedAt: Long,
    val tasks: List<Task>,
) {
    fun encode(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json { encodeDefaults = true }

        fun of(tasks: List<Task>): TaskBundle = TaskBundle(PlatformInfo.name, currentTimeMillis(), tasks)

        fun decode(text: String): TaskBundle = codec.decodeFromString(serializer(), text)
    }
}
