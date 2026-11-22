package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DiagramGroup
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DiagramNode
import io.github.krosstack.kscope.diagram.Omission
import io.github.krosstack.kscope.diagram.ReasonNode
import io.github.krosstack.kscope.diagram.emit.middleCut
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId

private val legend = listOf(
    "solid: exact",
    "dashed: probable, heuristic",
    "dotted ?: unresolved, not-analysed",
    "labels: relation; evidence counts; state counts",
    "selected: chosen symbol",
    "thick: inheritance; numbered: call position",
)

internal class FlowDiagramAssembler(private val reader: BoundedReader, private val level: FlowLevel) {
    fun assemble(selected: Symbol, flow: FlowGraph): Diagram {
        val ordered = flow.distance.entries.sortedWith(compareBy({ it.value }, { it.key.value })).map { it.key }
            .filter { level.draws(it) }
        val drawn = ordered.toSet()
        val links = flow.links.filter { it.from in drawn && (it.to == null || it.to in drawn) }
        val faces = ordered.associateWith { face(it) }
        val groupIds = faces.values.mapNotNull { it.group }.distinct().sortedBy { it.id }.withIndex()
            .associate { it.value to "g${it.index + 1}" }
        val nodes = ordered.mapIndexed { index, id ->
            node(id, "n${index + 1}", faces.getValue(id), groupIds, selected.id)
        }
        val reasonIds = links.mapNotNull { it.reason }.distinct().sorted().withIndex()
            .associate { it.value to "r${it.index + 1}" }
        return Diagram(
            DiagramKind.SYMBOL,
            "${level.title} ${(selected.qualifiedName ?: selected.name).middleCut()}",
            nodes,
            groups(nodes, groupIds),
            edges(links, ordered.zip(nodes.map { it.id }).toMap(), reasonIds),
            reasonIds.map { (code, id) -> ReasonNode(id, code, links.count { it.reason == code }) },
            omission(flow, level),
            legend + level.legendNotes,
        )
    }

    private fun omission(flow: FlowGraph, level: FlowLevel): Omission = Omission(
        nodes = 0,
        linksByState = emptyMap(),
        notExpanded = flow.notExpanded,
        membersNotRead = level.membersNotRead,
        callsNotDrawn = flow.undrawn,
        notInType = flow.unseen.getOrDefault(Unseen.NOT_IN_TYPE, 0),
        notCallable = flow.unseen.getOrDefault(Unseen.NOT_CALLABLE, 0),
        withoutSymbol = flow.unseen.getOrDefault(Unseen.WITHOUT_SYMBOL, 0),
        withinType = flow.unseen.getOrDefault(Unseen.WITHIN_TYPE, 0),
        beyondDepth = flow.unseen.getOrDefault(Unseen.BEYOND_DEPTH, 0),
        stopped = flow.unseen.getOrDefault(Unseen.STOPPED, 0),
    )

    private fun face(id: SymbolId): NodeFace = reader.described(id)?.let { level.face(it) } ?: NodeFace(id.value, null)

    private fun node(
        id: SymbolId,
        nodeId: String,
        face: NodeFace,
        groupIds: Map<GroupKey, String>,
        selected: SymbolId,
    ): DiagramNode = DiagramNode(
        nodeId,
        face.label.middleCut(),
        reader.described(id)?.kind?.id ?: "symbol",
        face.group?.let { groupIds[it] },
        id == selected,
        id.value,
    )

    private fun groups(nodes: List<DiagramNode>, groupIds: Map<GroupKey, String>): List<DiagramGroup> =
        groupIds.map { (key, id) ->
            DiagramGroup(id, key.label.middleCut(), nodes.filter { it.group == id }.map { it.id })
        }

    private fun edges(links: List<FlowLink>, ids: Map<SymbolId, String>, reasons: Map<String, String>) =
        links.mapNotNull { link ->
            val from = ids[link.from]
            val to = link.to?.let { ids[it] } ?: link.reason?.let { reasons[it] }
            if (from == null || to == null) null else edge(from, to, link)
        }

    private fun edge(from: String, to: String, link: FlowLink): DiagramEdge = DiagramEdge(
        from,
        to,
        link.label,
        link.state,
        mapOf(link.state to 1),
        mapOf(link.evidence.name to 1),
        link.position,
        link.counted?.let { mapOf(it to 1) }.orEmpty(),
    )
}
