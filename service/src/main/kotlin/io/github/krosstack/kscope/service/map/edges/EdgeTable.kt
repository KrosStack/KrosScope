package io.github.krosstack.kscope.service.map.edges

import io.github.krosstack.kscope.model.Relationship

/** The relationships of each node: those that leave it, those that reach it, and those that have no target. */
internal class EdgeLists(val outgoing: NodeLists, val incoming: NodeLists, val open: NodeLists)

/** What the relationships keep for the map, once each has been placed on the tree. */
internal class EdgeTable(
    val relationships: List<Relationship>,
    val fromNodes: IntArray,
    val toNodes: IntArray,
    val lists: EdgeLists,
)
