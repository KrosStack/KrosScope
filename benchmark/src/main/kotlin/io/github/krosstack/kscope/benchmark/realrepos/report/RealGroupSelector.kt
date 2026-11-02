package io.github.krosstack.kscope.benchmark.realrepos.report

import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealSplit

internal data class RealGroupSelector(
    val repository: String?,
    val split: RealSplit,
    val taskClass: RealTaskClass? = null,
) {
    fun matches(task: RealCommitTask): Boolean = task.split == split &&
        (repository == null || task.repository == repository) && (taskClass == null || taskClass.matches(task))
}
