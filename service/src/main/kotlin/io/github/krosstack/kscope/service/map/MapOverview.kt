package io.github.krosstack.kscope.service.map

import kotlinx.serialization.Serializable

/**
 * One node of the tree of the workspace. [parent] is the position of the parent in [MapOverview.nodes], and a parent
 * always comes before its children. [symbols], [unresolved] and [notAnalysed] count what is attached to this node
 * itself; a client adds up the nodes below it.
 */
@Serializable
public data class MapNode(
    public val id: String,
    public val parent: Int,
    public val type: String,
    public val label: String,
    public val lines: Int = 0,
    public val symbols: Int = 0,
    public val artifactType: String? = null,
    public val category: String? = null,
    public val generated: Boolean = false,
    public val hash: String? = null,
    public val commits: Int = 0,
    public val unresolved: Int = 0,
    public val notAnalysed: Int = 0,
    public val path: String? = null,
    public val traits: List<String>? = null,
    public val platform: String? = null,
)

/**
 * The tree of the workspace and its aggregated relationships. [edges] holds five numbers per edge: the position of
 * the node it leaves, the position of the node it reaches, the index in [kinds], the index in [certainties] and how
 * many relationships it stands for. [unplaced] counts the resolved relationships that have an end outside the tree.
 */
@Serializable
public data class MapOverview(
    public val workspace: String,
    public val level: String? = null,
    public val kinds: List<String>,
    public val certainties: List<String>,
    public val nodes: List<MapNode>,
    public val edges: List<Int>,
    public val unplaced: Int = 0,
)
