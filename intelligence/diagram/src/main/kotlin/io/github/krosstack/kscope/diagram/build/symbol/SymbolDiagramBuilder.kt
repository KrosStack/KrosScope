package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramRequest
import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.spi.graph.GraphReader

internal class SymbolDiagramBuilder(
    private val reader: GraphReader,
    private val callBudget: Int = READER_CALL_BUDGET,
) {
    fun build(request: DiagramRequest, finish: (Diagram) -> DiagramResult): DiagramResult =
        when (val choice = SymbolSelection(reader).choose(request.node)) {
            is Choice.Refused -> choice.result
            is Choice.Chosen -> finish(draw(choice.symbol, request))
        }

    private fun draw(selected: Symbol, request: DiagramRequest): Diagram {
        val bounded = BoundedReader(reader, ReadBudget(callBudget))
        bounded.remember(selected)
        val level = if (selected.kind.category == SymbolCategory.TYPE) TypeLevel(bounded) else CallLevel(bounded)
        val visited = request.links.coerceIn(0, Int.MAX_VALUE - 1) + 1
        val flow = FlowWalk(level, WalkLimits(request.depth, visited), selected.id).walk()
        return FlowDiagramAssembler(bounded, level).assemble(selected, flow)
    }
}
