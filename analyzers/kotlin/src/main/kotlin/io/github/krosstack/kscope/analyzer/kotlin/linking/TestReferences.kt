package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private val usageKinds = setOf(
    RelationKind.CALLS,
    RelationKind.REFERENCES,
    RelationKind.TYPE_USES,
    RelationKind.EXTENDS,
    RelationKind.IMPLEMENTS,
)

/**
 * One use that a test makes of the workspace: [chain] is the declaration it reaches followed by the declarations that
 * contain it, so that the outermost one is last.
 */
internal class Reached(val chain: List<Symbol>) {
    val outermost: Symbol get() = chain.last()
}

/** The workspace declarations that a test class and its members use, on the evidence of the compiler. */
internal class TestReferences(private val graph: GraphReader) {
    private val chains = HashMap<SymbolId, List<Symbol>>()

    fun reachedBy(test: Symbol): List<Reached> = withMembers(test).flatMap { member ->
        graph.outgoing(NodeRef.OfSymbol(member.id), usageKinds)
            .filter { it.evidenceClass == EvidenceClass.COMPILER }
            .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id }
            .map { chainOf(it) }
            .filter { it.isNotEmpty() }
            .map { Reached(it) }
    }

    private fun withMembers(symbol: Symbol, reached: MutableSet<SymbolId> = mutableSetOf(symbol.id)): List<Symbol> =
        listOf(symbol) + graph.symbols(SymbolQuery(container = symbol.id, limit = ALL))
            .filter { reached.add(it.id) }
            .flatMap { withMembers(it, reached) }

    private fun chainOf(id: SymbolId): List<Symbol> = chains.getOrPut(id) {
        val reached = mutableSetOf<SymbolId>()
        generateSequence(graph.symbol(id)) { symbol -> symbol.container?.let(graph::symbol) }
            .takeWhile { reached.add(it.id) }
            .toList()
    }
}
