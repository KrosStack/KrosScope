package io.github.krosstack.kscope.service.map.detail

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.map.MapData
import io.github.krosstack.kscope.service.map.MapLink
import io.github.krosstack.kscope.service.map.MapNode
import io.github.krosstack.kscope.service.map.MapOpenLink
import io.github.krosstack.kscope.service.map.tree.NONE
import io.github.krosstack.kscope.service.map.wireName

/** Describes the relationships that leave or reach a node. */
internal class MapLinks(private val nodes: List<MapNode>, private val data: MapData) {
    fun link(position: Int): MapLink {
        val relationship = data.edges.relationships[position]
        val resolved = relationship.resolution as Resolution.Resolved
        val from = data.edges.fromNodes[position]
        val to = data.edges.toNodes[position]
        return MapLink(
            kind = relationship.kind.wireName(),
            subKind = relationship.subKind,
            certainty = resolved.certainty.wireName(),
            evidence = relationship.evidenceClass.wireName(),
            line = relationship.site?.range?.startLine,
            sitePath = relationship.site?.let { data.locations.pathOf(it.artifact) },
            fromSymbol = symbolId(relationship.from),
            toSymbol = symbolId(resolved.target),
            fromNode = from,
            toNode = to,
            fromLabel = label(relationship.from, from),
            toLabel = label(resolved.target, to),
            attributes = relationship.attributes,
        )
    }

    fun open(position: Int): MapOpenLink {
        val relationship = data.edges.relationships[position]
        val (state, hint, reason) = when (val resolution = relationship.resolution) {
            is Resolution.Unresolved -> Triple("unresolved", resolution.hint, resolution.reason.value)
            is Resolution.NotAnalysed -> Triple("not-analysed", resolution.hint, resolution.reason.value)
            is Resolution.Resolved -> error("A resolved relationship is not open")
        }
        return MapOpenLink(
            fromSymbol = symbolId(relationship.from),
            fromLabel = label(relationship.from, data.edges.fromNodes[position]),
            kind = relationship.kind.wireName(),
            state = state,
            hint = hint,
            reason = reason,
            line = relationship.site?.range?.startLine,
        )
    }

    private fun symbolId(ref: NodeRef): String? = (ref as? NodeRef.OfSymbol)?.id?.value

    private fun label(ref: NodeRef, node: Int): String {
        if (node != NONE && ref !is NodeRef.OfSymbol) return nodes[node].let { it.path ?: it.label }
        return when (ref) {
            is NodeRef.OfSymbol -> symbolLabel(ref)
            is NodeRef.OfArtifact -> data.locations.pathOf(ref.ref)
            is NodeRef.OfModule -> ref.id.value
            is NodeRef.OfSourceSet -> ref.id.value
            is NodeRef.OfExternal -> ref.coordinate
        }
    }

    private fun symbolLabel(ref: NodeRef.OfSymbol): String {
        val symbol = data.index.symbols.positions[ref.id.value]?.let { data.symbols[it] }
        return symbol?.let { it.qualifiedName ?: it.name } ?: ref.id.value
    }
}
