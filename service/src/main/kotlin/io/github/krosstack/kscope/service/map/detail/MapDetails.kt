package io.github.krosstack.kscope.service.map.detail

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.map.MapData
import io.github.krosstack.kscope.service.map.MapNode
import io.github.krosstack.kscope.service.map.MapNodeDetail
import io.github.krosstack.kscope.service.map.MapSymbol
import io.github.krosstack.kscope.service.map.wireName

/** Describes what is attached directly to one node. */
internal class MapDetails(nodes: List<MapNode>, private val data: MapData) {
    private val links = MapLinks(nodes, data)

    fun of(id: String, position: Int): MapNodeDetail {
        val outgoing = data.edges.lists.outgoing
        val incoming = data.edges.lists.incoming
        val open = data.edges.lists.open
        return MapNodeDetail(
            id = id,
            symbols = symbols(position),
            outgoing = outgoing.items(position, LINK_LIMIT).map(links::link),
            incoming = incoming.items(position, LINK_LIMIT).map(links::link),
            open = open.items(position, OPEN_LIMIT).map(links::open),
            truncated = outgoing.size(position) > LINK_LIMIT || incoming.size(position) > LINK_LIMIT ||
                open.size(position) > OPEN_LIMIT,
        )
    }

    private fun symbols(position: Int): List<MapSymbol> = data.symbolLists.items(position, Int.MAX_VALUE)
        .map { data.symbols[it] }
        .sortedWith(compareBy({ it.declaration?.range?.startLine ?: Int.MAX_VALUE }, { it.id.value }))
        .map(::view)

    private fun view(symbol: Symbol) = MapSymbol(
        id = symbol.id.value,
        name = symbol.name,
        kind = symbol.kind.id,
        category = symbol.kind.category.wireName(),
        container = symbol.container?.value,
        line = symbol.declaration?.range?.startLine,
        endLine = symbol.declaration?.range?.endLine,
        signature = symbol.signature,
        visibility = symbol.visibility?.wireName(),
        flags = symbol.flags.map { it.wireName() }.sorted(),
        generated = symbol.origin is SymbolOrigin.Generated,
    )

    private companion object {
        const val LINK_LIMIT = 2_000
        const val OPEN_LIMIT = 500
    }
}
