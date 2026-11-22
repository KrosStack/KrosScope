package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SymbolId

internal data class FlowLink(
    val from: SymbolId,
    val to: SymbolId?,
    val reason: String?,
    val label: String,
    val state: DrawnState,
    val evidence: EvidenceClass,
    val position: Int?,
    val origin: Relationship,
    val counted: String?,
)

internal data class Reading(val links: List<FlowLink>, val complete: Boolean, val dropped: List<Dropped> = emptyList())

internal data class Share(val directions: Int, val others: Int) {
    val parts: Int get() = directions + others
}

internal data class FlowGraph(
    val distance: Map<SymbolId, Int>,
    val links: List<FlowLink>,
    val notExpanded: Int,
    val undrawn: Map<String, Int>,
    val unseen: Map<Unseen, Int>,
)

internal data class WalkLimits(val depth: Int, val nodes: Int)
