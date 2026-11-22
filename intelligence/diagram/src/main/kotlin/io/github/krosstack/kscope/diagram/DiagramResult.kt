package io.github.krosstack.kscope.diagram

import io.github.krosstack.kscope.model.Symbol

public sealed interface DiagramResult {
    public data class Built(public val diagram: Diagram) : DiagramResult

    public data class BudgetTooSmall(public val minimumCharacters: Int) : DiagramResult

    public data class NotFound(public val node: String?) : DiagramResult

    public data class NotSupported(public val symbol: Symbol) : DiagramResult

    /** The [candidates] are in canonical order. */
    public data class Ambiguous(public val node: String, public val candidates: List<Symbol>) : DiagramResult
}
