package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.SymbolId

internal data class Way(val downstream: Boolean, val upstream: Boolean)

internal class Coverage(selected: SymbolId) {
    val seenDownstream = mutableSetOf(selected)
    val seenUpstream = mutableSetOf(selected)
    val downstreamRead = mutableSetOf<SymbolId>()
    val upstreamRead = mutableSetOf<SymbolId>()
    val followed = mutableSetOf<SymbolId>()
    val skipped = mutableSetOf<SymbolId>()

    fun see(unit: SymbolId, way: Way) {
        if (way.downstream) seenDownstream += unit
        if (way.upstream) seenUpstream += unit
    }

    fun unread(visited: Set<SymbolId>): Int = skipped.size + visited.count {
        (it in seenDownstream && it !in downstreamRead) || (it in seenUpstream && it !in upstreamRead)
    }
}
