package io.github.krosstack.kscope.service.map.edges

import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.map.tree.NONE
import io.github.krosstack.kscope.service.map.tree.NodeIndex

/**
 * Places every relationship but `contains` on the tree. Resolved ones are counted per pair of nodes, kind and
 * certainty unless an end is outside the tree or both ends are one node; the others are counted on the node they
 * leave.
 */
internal class EdgeScan(
    private val relationships: List<Relationship>,
    private val index: NodeIndex,
    private val nodeCount: Int,
) {
    private val fromNodes = IntArray(relationships.size) { NONE }
    private val toNodes = IntArray(relationships.size) { NONE }
    private val outgoingKeys = IntArray(relationships.size) { NONE }
    private val incomingKeys = IntArray(relationships.size) { NONE }
    private val openKeys = IntArray(relationships.size) { NONE }
    private val aggregated = LongArray(relationships.size)
    private var aggregatedCount = 0
    val unresolved = IntArray(nodeCount)
    val notAnalysed = IntArray(nodeCount)
    var unplaced = 0
        private set

    fun run(): EdgeScan {
        relationships.forEachIndexed { position, relationship ->
            if (relationship.kind != RelationKind.CONTAINS) visit(position, relationship)
        }
        return this
    }

    fun table(): EdgeTable = EdgeTable(
        relationships,
        fromNodes,
        toNodes,
        EdgeLists(
            NodeLists.group(nodeCount, outgoingKeys),
            NodeLists.group(nodeCount, incomingKeys),
            NodeLists.group(nodeCount, openKeys),
        ),
    )

    fun edges(): IntArray = EdgeKeys.flatten(aggregated.copyOf(aggregatedCount).also { it.sort() })

    private fun visit(position: Int, relationship: Relationship) {
        fromNodes[position] = index.of(relationship.from)
        when (val resolution = relationship.resolution) {
            is Resolution.Resolved -> resolved(position, relationship, resolution)
            is Resolution.Unresolved -> leaving(position, unresolved)
            is Resolution.NotAnalysed -> leaving(position, notAnalysed)
        }
    }

    private fun resolved(position: Int, relationship: Relationship, resolution: Resolution.Resolved) {
        val from = fromNodes[position]
        val to = index.of(resolution.target)
        toNodes[position] = to
        outgoingKeys[position] = from
        if (to != from) incomingKeys[position] = to
        if (from == NONE || to == NONE) {
            unplaced++
        } else if (from != to) {
            aggregated[aggregatedCount++] = EdgeKeys.pack(from, to, relationship.kind, resolution.certainty)
        }
    }

    private fun leaving(position: Int, counts: IntArray) {
        val from = fromNodes[position]
        openKeys[position] = from
        if (from != NONE) counts[from]++
    }
}
