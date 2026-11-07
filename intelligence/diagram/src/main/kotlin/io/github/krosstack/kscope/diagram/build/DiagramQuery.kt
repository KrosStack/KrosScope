package io.github.krosstack.kscope.diagram.build

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramGroup
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramNode
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.diagram.Omission
import io.github.krosstack.kscope.diagram.ReasonNode
import io.github.krosstack.kscope.diagram.emit.middleCut
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery

internal class DiagramQuery(private val reader: GraphReader, private val kind: DiagramKind) {
    private val components = kind == DiagramKind.COMPONENTS
    private val structure = reader.structure()
    private val repositories = structure.repositories.sortedWith(compareBy({ it.name }, { it.id.value }))
    private val modules = structure.modules.sortedWith(compareBy({ it.path }, { it.id.value }))
    private val moduleRepositories = structure.buildRoots.associate { it.id to it.repository.value }
        .let { roots -> modules.associate { it.id.value to roots[it.buildRoot] } }
    private val names = if (components) {
        repositories.map { it.id.value to it.name }
    } else {
        modules.map { it.id.value to it.path }
    }
    private val ids = names.mapIndexed { index, name -> name.first to "n${index + 1}" }.toMap()
    private val groups = repositories.mapIndexed { index, repo -> repo.id.value to "g${index + 1}" }.toMap()
    private val kinds = if (components) {
        setOf(RelationKind.DEPENDS_ON, RelationKind.BINDS)
    } else {
        setOf(RelationKind.DEPENDS_ON)
    }
    private val facts = reader.relationships(RelationshipQuery(kinds = kinds, limit = Int.MAX_VALUE))
    private val reasonIds = facts.mapNotNull { reason(it.resolution) }.distinct().sorted()
        .mapIndexed { index, code -> code to "r${index + 1}" }.toMap()

    fun build(request: DiagramRequest): Diagram {
        val edges = facts.mapNotNull(::project)
        val scope = scope(request, edges)
        val nodes = names.filter { ids.getValue(it.first) in scope }.map { (key, name) ->
            DiagramNode(
                ids.getValue(key),
                name.middleCut(),
                if (components) "repository" else "module",
                if (components) null else groups[moduleRepositories[key]],
            )
        }
        val reasons = reasonIds.map { (code, id) ->
            ReasonNode(id, code, edges.count { it.to == id && it.from in scope })
        }.filter { it.id in scope && it.count > 0 }
        return Diagram(
            kind,
            if (components) "Components" else "Modules",
            nodes,
            groupNodes(nodes),
            edges.filter { it.from in scope && it.to in scope },
            reasons,
            Omission(0, emptyMap()),
            listOf(
                "solid: exact",
                "dashed: probable, heuristic",
                "dotted ?: unresolved, not-analysed",
                "labels: relation; evidence counts; state counts",
                "cycle: dependency cycle",
            ),
        )
    }

    private fun groupNodes(nodes: List<DiagramNode>): List<DiagramGroup> = repositories.mapNotNull { repo ->
        val id = groups.getValue(repo.id.value)
        nodes.filter { it.group == id }.map { it.id }.takeIf { it.isNotEmpty() }
            ?.let { DiagramGroup(id, repo.name.middleCut(), it) }
    }

    private fun project(fact: Relationship): DiagramEdge? {
        val from = ids[owner(fact.from)]
        val resolution = fact.resolution
        val to = if (resolution is Resolution.Resolved) {
            ids[owner(resolution.target)]
        } else {
            reasonIds[reason(resolution)]
        }
        val internal = from == to
        if (from == null || to == null || internal) return null
        val state = when (resolution) {
            is Resolution.Resolved -> DrawnState.valueOf(resolution.certainty.name)
            is Resolution.Unresolved -> DrawnState.UNRESOLVED
            is Resolution.NotAnalysed -> DrawnState.NOT_ANALYSED
        }
        val label = listOfNotNull(fact.kind.name.lowercase(), fact.subKind).joinToString(":")
        return DiagramEdge(from, to, label, state, mapOf(state to 1), mapOf(fact.evidenceClass.name to 1))
    }

    private fun owner(node: NodeRef): String? {
        if (!components) {
            return (node as? NodeRef.OfModule)?.id?.value
                ?: ((node as? NodeRef.OfSymbol)?.id?.identity() as? ParsedSymbolId.Valid)?.identity?.unit
        }
        return when (node) {
            is NodeRef.OfModule -> moduleRepositories[node.id.value]

            is NodeRef.OfSourceSet -> structure.sourceSets.find { it.id == node.id }?.module?.value
                ?.let { moduleRepositories[it] }

            is NodeRef.OfArtifact -> node.ref.repository.value

            is NodeRef.OfSymbol -> reader.symbol(node.id)?.declaration?.artifact?.repository?.value
                ?: moduleRepositories[(node.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit]

            is NodeRef.OfExternal -> null
        }
    }

    private fun reason(resolution: Resolution): String? = when (resolution) {
        is Resolution.Resolved -> null
        is Resolution.Unresolved -> resolution.reason.value
        is Resolution.NotAnalysed -> resolution.reason.value
    }

    private fun scope(request: DiagramRequest, edges: List<DiagramEdge>): Set<String> {
        if (request.node == null) return ids.values.toSet() + edges.map { it.to }
        val roots = names.filter { request.node == it.first || request.node == it.second }
            .map { ids.getValue(it.first) }
        require(roots.isNotEmpty()) { "Unknown diagram node: ${request.node}" }
        val reached = roots.toMutableSet()
        repeat(request.depth.coerceAtMost(ids.size)) { _ ->
            reached += edges.filter { it.from in reached || (it.to in reached && it.to in ids.values) }
                .flatMap { listOf(it.from, it.to) }
        }
        return reached
    }
}
