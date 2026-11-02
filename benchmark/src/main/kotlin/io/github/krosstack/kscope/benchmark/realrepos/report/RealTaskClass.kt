package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import kotlinx.serialization.Serializable

@Serializable
internal enum class RealTaskClassKind { LEVEL, NAMED }

@Serializable
internal data class RealTaskClass(val kind: RealTaskClassKind, val value: String) {
    val label: String
        get() = when (kind) {
            RealTaskClassKind.LEVEL -> "Level $value"
            RealTaskClassKind.NAMED -> if (value == NAMED) "Named" else "Not named"
        }

    fun matches(task: RealCommitTask): Boolean = when (kind) {
        RealTaskClassKind.LEVEL -> task.level?.toString() == value
        RealTaskClassKind.NAMED -> task.named?.let { if (it) NAMED else NOT_NAMED } == value
    }

    companion object {
        const val NAMED = "named"
        const val NOT_NAMED = "not-named"

        fun of(tasks: List<RealCommitTask>): List<RealTaskClass> {
            val levels = tasks.mapNotNull { it.level }.distinct().sorted()
                .map { RealTaskClass(RealTaskClassKind.LEVEL, it.toString()) }
            val named = listOf(NAMED, NOT_NAMED).map { RealTaskClass(RealTaskClassKind.NAMED, it) }
                .filter { candidate -> tasks.any { candidate.matches(it) } }
            return levels + named
        }
    }
}
