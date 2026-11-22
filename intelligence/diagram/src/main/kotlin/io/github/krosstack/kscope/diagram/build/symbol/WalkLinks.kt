package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.SymbolId

internal class WalkLinks(
    private val distance: Map<SymbolId, Int>,
    private val leaving: Map<SymbolId, Reading>,
    private val entering: Map<SymbolId, Reading>,
) {
    private val expanded: List<FlowLink> by lazy { distance.keys.flatMap { leaving[it]?.links.orEmpty() } }

    fun drawn(): List<FlowLink> =
        (expanded + reported()).filter { it.from in distance && (it.to == null || it.to in distance) }

    fun undrawn(followed: Set<SymbolId>, kinds: Map<SymbolId, String>): Map<String, Int> =
        followed.flatMap { leaving[it]?.links.orEmpty() }.mapNotNull { link -> link.to?.let { kinds[it] } }
            .groupingBy { it }.eachCount().toSortedMap()

    private fun reported(): List<FlowLink> {
        val covered = expanded.groupingBy { it }.eachCount().toMutableMap()
        return distance.keys.flatMap { entering[it]?.links.orEmpty() }.filter { link ->
            leaving[link.from]?.complete != true && !covered.consume(link)
        }
    }
}

private fun MutableMap<FlowLink, Int>.consume(link: FlowLink): Boolean {
    val left = this[link] ?: 0
    if (left > 0) this[link] = left - 1
    return left > 0
}
