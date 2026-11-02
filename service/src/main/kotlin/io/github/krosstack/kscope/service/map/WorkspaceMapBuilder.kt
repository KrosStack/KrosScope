package io.github.krosstack.kscope.service.map

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.service.map.edges.EdgeKeys
import io.github.krosstack.kscope.service.map.edges.EdgeScan
import io.github.krosstack.kscope.service.map.edges.NodeLists
import io.github.krosstack.kscope.service.map.tree.MapFacts
import io.github.krosstack.kscope.service.map.tree.MapTreeBuilder
import io.github.krosstack.kscope.service.map.tree.NONE
import io.github.krosstack.kscope.service.map.tree.NodeIndex
import io.github.krosstack.kscope.service.map.tree.SymbolPlacer
import io.github.krosstack.kscope.service.map.tree.TreeNode

/** Builds the map of the facts of one index; two builds of the same facts are equal. */
internal class WorkspaceMapBuilder(private val facts: MapFacts) {
    fun build(): WorkspaceMap {
        val tree = MapTreeBuilder(facts, externalCoordinates()).build()
        val symbolNodes = SymbolPlacer(tree.index, facts.symbols).place()
        val index = NodeIndex(tree.index, symbolNodes)
        val scan = EdgeScan(facts.relationships, index, tree.nodes.size).run()
        val symbolLists = NodeLists.group(tree.nodes.size, symbolNodes.nodes)
        val nodes = tree.nodes.map { node ->
            val position = node.position
            node.toMapNode(symbolLists.size(position), scan.unresolved[position], scan.notAnalysed[position])
        }
        val overview = MapOverview(
            workspace = facts.workspace,
            level = facts.level,
            kinds = EdgeKeys.KINDS.map { it.wireName() },
            certainties = EdgeKeys.CERTAINTIES.map { it.wireName() },
            nodes = nodes,
            edges = scan.edges().asList(),
            unplaced = scan.unplaced,
        )
        val ids = nodes.withIndex().associate { it.value.id to it.index }
        val locations = WorkspaceLocations(facts.structure.repositories)
        return WorkspaceMap(overview, MapData(ids, index, facts.symbols, symbolLists, scan.table(), locations))
    }

    private fun externalCoordinates(): Set<String> = buildSet {
        facts.relationships.filter { it.kind != RelationKind.CONTAINS }.forEach { relationship ->
            externalOf(relationship.from)?.let(::add)
            (relationship.resolution as? Resolution.Resolved)?.let { externalOf(it.target) }?.let(::add)
        }
        facts.symbols.filter { it.declaration == null }.forEach { symbol ->
            (symbol.origin as? SymbolOrigin.Library)?.coordinate?.let(::add)
        }
    }

    private fun externalOf(ref: NodeRef): String? = (ref as? NodeRef.OfExternal)?.coordinate
}

private fun TreeNode.toMapNode(symbols: Int, unresolved: Int, notAnalysed: Int) = MapNode(
    id = id,
    parent = parent?.position ?: NONE,
    type = type,
    label = label,
    lines = facts.lines,
    symbols = symbols,
    artifactType = facts.artifactType,
    category = facts.category,
    generated = facts.generated,
    hash = facts.hash,
    commits = facts.commits,
    unresolved = unresolved,
    notAnalysed = notAnalysed,
    path = facts.path,
    traits = facts.traits,
    platform = facts.platform,
)
