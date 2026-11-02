package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextGraph
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.Relationship

internal class UnresolvedCounts(unresolved: List<Relationship>, graph: ContextGraph) {
    private val byFile: Map<ArtifactRef, Int> =
        unresolved.mapNotNull { graph.artifact(it.from) }.groupingBy { it }.eachCount()

    fun of(file: ArtifactRef): Int = byFile[file] ?: 0

    fun ofAll(files: Set<ArtifactRef>): Int = files.sumOf(::of)
}
