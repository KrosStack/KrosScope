package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private val testRoles = setOf(SourceSetRole.UNIT_TEST, SourceSetRole.INSTRUMENTED_TEST)

/** The test classes of the workspace: the outermost types flagged as tests in a source set that holds tests. */
internal class TestClasses(private val graph: GraphReader, private val sourceSets: SourceSetModel) {
    fun outermost(): List<Symbol> {
        val flagged = graph.symbols(SymbolQuery(flag = SymbolFlag.TEST, limit = ALL))
        val flaggedIds = flagged.map { it.id }.toSet()
        return flagged.filter { symbol ->
            val isOutermost = symbol.container == null || symbol.container !in flaggedIds
            symbol.kind.category == SymbolCategory.TYPE && isOutermost && isInTestSourceSet(symbol)
        }
    }

    private fun isInTestSourceSet(symbol: Symbol): Boolean =
        symbol.scope.sourceSet?.let { sourceSets[it] }?.role in testRoles
}
