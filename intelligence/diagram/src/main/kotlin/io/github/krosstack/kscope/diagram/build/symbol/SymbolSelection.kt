package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramResult
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class SymbolSelection(private val reader: GraphReader) {
    fun choose(node: String?): Choice {
        val candidates = node?.let { candidates(it) }.orEmpty()
        return when {
            node == null -> Choice.Refused(DiagramResult.NotFound(null))
            candidates.isEmpty() -> Choice.Refused(DiagramResult.NotFound(node))
            candidates.size > 1 -> Choice.Refused(DiagramResult.Ambiguous(node, candidates))
            else -> supported(candidates.single())
        }
    }

    private fun candidates(node: String): List<Symbol> = listOfNotNull(reader.symbol(SymbolId(node)))
        .ifEmpty { reader.symbols(SymbolQuery(qualifiedName = node)) }
        .ifEmpty { reader.symbols(SymbolQuery(name = node)) }
        .distinctBy { it.id }
        .sortedBy { it.id.value }

    private fun supported(candidate: Symbol): Choice {
        val symbol = reader.symbol(candidate.id) ?: candidate
        return when (symbol.kind.category) {
            SymbolCategory.TYPE, SymbolCategory.CALLABLE -> Choice.Chosen(symbol)
            else -> Choice.Refused(DiagramResult.NotSupported(symbol))
        }
    }
}
