package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolId

internal const val MERGED_LABEL: String = "depends"

internal fun Relationship.drawnState(): DrawnState = when (val outcome = resolution) {
    is Resolution.Resolved -> DrawnState.valueOf(outcome.certainty.name)
    is Resolution.Unresolved -> DrawnState.UNRESOLVED
    is Resolution.NotAnalysed -> DrawnState.NOT_ANALYSED
}

internal fun Relationship.reasonCode(): String? = when (val outcome = resolution) {
    is Resolution.Resolved -> null
    is Resolution.Unresolved -> outcome.reason.value
    is Resolution.NotAnalysed -> outcome.reason.value
}

internal fun Relationship.targetSymbol(): SymbolId? =
    ((resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id

internal fun Relationship.sourceSymbol(): SymbolId? = (from as? NodeRef.OfSymbol)?.id

internal fun Relationship.outcome(
    from: SymbolId?,
    to: SymbolId?,
    position: Int? = null,
    merged: Boolean = false,
): Outcome {
    val reason = reasonCode()
    if (from == null || (to == null && reason == null)) return Outcome.Lost(Dropped(this, Unseen.WITHOUT_SYMBOL))
    val label = if (merged) MERGED_LABEL else kind.name.lowercase()
    val counted = if (merged) kind.name.lowercase().replace('_', '-') else null
    return Outcome.Linked(FlowLink(from, to, reason, label, drawnState(), evidenceClass, position, this, counted))
}

internal fun Relationship.lost(reason: Unseen): Outcome = Outcome.Lost(Dropped(this, reason))
