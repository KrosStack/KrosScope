package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.service.map.detail.MapDetails
import io.github.krosstack.kscope.service.map.edges.EdgeKeys
import io.github.krosstack.kscope.service.map.edges.EdgeLists
import io.github.krosstack.kscope.service.map.edges.EdgeTable
import io.github.krosstack.kscope.service.map.edges.NodeLists
import io.github.krosstack.kscope.service.map.tree.NodeIndex
import io.github.krosstack.kscope.service.map.tree.SymbolNodes
import io.github.krosstack.kscope.service.map.tree.TreeIndex

/**
 * The tree of a workspace with its aggregated relationships, and what is attached to each node. It holds copied
 * data only, no reference to the store or to the workspace, and it never changes, so any thread can read it.
 */
public class WorkspaceMap internal constructor(public val overview: MapOverview, private val data: MapData) {
    /** What is attached directly to the node called [nodeId], or null when the map has no such node. */
    public fun detail(nodeId: String): MapNodeDetail? =
        data.nodeIds[nodeId]?.let { MapDetails(overview.nodes, data).of(nodeId, it) }

    internal fun positionOf(nodeId: String): Int? = data.nodeIds[nodeId]

    internal fun placeOf(ref: NodeRef): Int = data.index.of(ref)

    internal fun symbolOf(id: SymbolId): Symbol? = data.index.symbols.positions[id.value]?.let { data.symbols[it] }

    public companion object {
        /** The map of a workspace that has no index: it has no nodes. */
        public fun empty(workspace: String): WorkspaceMap {
            val overview = MapOverview(
                workspace = workspace,
                kinds = EdgeKeys.KINDS.map { it.wireName() },
                certainties = EdgeKeys.CERTAINTIES.map { it.wireName() },
                nodes = emptyList(),
                edges = emptyList(),
            )
            return WorkspaceMap(overview, emptyData())
        }

        private fun emptyData(): MapData {
            val none = NodeLists.group(0, IntArray(0))
            val edges = EdgeTable(emptyList(), IntArray(0), IntArray(0), EdgeLists(none, none, none))
            val tree = TreeIndex(emptyMap(), emptyMap(), emptyMap(), emptyMap())
            val index = NodeIndex(tree, SymbolNodes(emptyMap(), IntArray(0)))
            return MapData(emptyMap(), index, emptyList(), none, edges, WorkspaceLocations.SINGLE)
        }
    }
}
