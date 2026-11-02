package sample.tasktide.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Priority(val level: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    URGENT(3);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NORMAL
    }
}
