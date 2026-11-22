package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.SymbolId

internal class Ends(
    private val distance: Map<SymbolId, Int>,
    private val followed: Set<SymbolId>,
    private val refusals: Map<SymbolId, Unseen>,
) {
    fun leaving(unit: SymbolId, link: FlowLink): Unseen? {
        val target = link.to
        return when {
            target == null || target in distance -> null
            unit in followed -> missing(target)
            else -> Unseen.BEYOND_DEPTH
        }
    }

    fun inside(link: FlowLink): Boolean = link.to != null && link.from in distance && link.to in distance

    fun entering(link: FlowLink): Unseen? = if (link.from in distance) null else missing(link.from)

    private fun missing(end: SymbolId): Unseen = refusals[end] ?: Unseen.STOPPED
}
