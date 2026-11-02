package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId

/**
 * The refinement (`dependsOn`) edges between analysis modules. A source set that has no files is not an analysis
 * module, so an edge to it leads on to the source sets it refines.
 */
internal class GroupGraph(groups: List<SourceSetGroup>, allSourceSets: List<SourceSet>) {
    private val groupBySourceSet: Map<SourceSetId, SourceSetGroup> =
        groups.flatMap { group -> group.members.map { it.id to group } }.toMap()
    private val parents: Map<SourceSetId, List<SourceSetId>> = allSourceSets.associate { it.id to it.dependsOn }

    fun groupOf(id: SourceSetId): SourceSetGroup? = groupBySourceSet[id]

    /** The groups a group directly refines. */
    fun refined(group: SourceSetGroup): List<SourceSetGroup> =
        group.members.flatMap { nearestGroupsAbove(it.id) }.distinct().filter { it !== group }

    /** The group itself followed by everything it refines, directly or not. */
    fun withRefined(group: SourceSetGroup): List<SourceSetGroup> {
        val seen = linkedSetOf(group)
        val pending = ArrayDeque(listOf(group))
        while (pending.isNotEmpty()) {
            refined(pending.removeFirst()).filter(seen::add).forEach(pending::addLast)
        }
        return seen.toList()
    }

    fun refines(group: SourceSetGroup, other: SourceSetGroup): Boolean = group !== other && other in withRefined(group)

    /** Every source set that [id] refines, directly or not, whether or not it is an analysis module. */
    fun ancestorsOf(id: SourceSetId): Set<SourceSetId> {
        val seen = linkedSetOf<SourceSetId>()
        val pending = ArrayDeque(parents[id].orEmpty())
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (seen.add(next)) pending.addAll(parents[next].orEmpty())
        }
        return seen
    }

    private fun nearestGroupsAbove(id: SourceSetId): List<SourceSetGroup> {
        val found = linkedSetOf<SourceSetGroup>()
        val visited = mutableSetOf<SourceSetId>()
        val pending = ArrayDeque(parents[id].orEmpty())
        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (!visited.add(next)) continue
            val group = groupBySourceSet[next]
            if (group == null) pending.addAll(parents[next].orEmpty()) else found += group
        }
        return found.toList()
    }
}
