package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit

internal object CorpusSelection {
    fun split(tasks: List<RealCommitTask>): List<RealCommitTask> {
        val ordered = tasks.sortedWith(compareBy<RealCommitTask> { it.committedAt }.thenBy { it.commit })
        return ordered.mapIndexed { index, task ->
            task.copy(split = if (index < ordered.size / 2) RealSplit.DEV else RealSplit.TEST)
        }
    }

    fun verification(tasks: List<RealCommitTask>): List<RealCommitTask> {
        val groups = tasks.groupBy { it.repository }.toSortedMap()
        val quotas = groups.mapValues { 0 }.toMutableMap()
        var remaining = minOf(VERIFY_COUNT, tasks.size)
        while (remaining > 0) {
            for ((repository, entries) in groups) {
                if (remaining > 0 && quotas.getValue(repository) < entries.size) {
                    quotas[repository] = quotas.getValue(repository) + 1
                    remaining--
                }
            }
        }
        val selected = groups.flatMap { (repository, entries) -> spread(entries, quotas.getValue(repository)) }
            .map { it.id }.toSet()
        return tasks.map { it.copy(verify = it.id in selected) }
    }

    private fun spread(tasks: List<RealCommitTask>, count: Int): List<RealCommitTask> = when (count) {
        0 -> emptyList()
        1 -> listOf(tasks[tasks.size / 2])
        else -> (0 until count).map { tasks[it * (tasks.size - 1) / (count - 1)] }
    }

    private const val VERIFY_COUNT = 5
}
