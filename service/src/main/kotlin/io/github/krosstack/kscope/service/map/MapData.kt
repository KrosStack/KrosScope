package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.service.map.edges.EdgeTable
import io.github.krosstack.kscope.service.map.edges.NodeLists
import io.github.krosstack.kscope.service.map.tree.NodeIndex

/**
 * What a map keeps beside its overview to answer questions about one node. The symbols and relationships are the
 * lists of the index as they were read, shared and never changed; the rest is arrays of positions.
 */
internal data class MapData(
    val nodeIds: Map<String, Int>,
    val index: NodeIndex,
    val symbols: List<Symbol>,
    val symbolLists: NodeLists,
    val edges: EdgeTable,
    val locations: WorkspaceLocations,
)
