package io.github.krosstack.kscope.diagram

public sealed interface DiagramResult {
    public data class Built(public val diagram: Diagram) : DiagramResult

    public data class BudgetTooSmall(public val minimumCharacters: Int) : DiagramResult
}
