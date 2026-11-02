package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/** What the linker made of a graph: the facts it emitted, read back in the terms of the tests. */
internal class Linked(private val graph: JniGraph, val facts: List<Fact>) {
    val symbols: List<Symbol> get() = facts.filterIsInstance<Fact.OfSymbol>().map { it.value }
    val relationships: List<Relationship> get() = facts.filterIsInstance<Fact.OfRelationship>().map { it.value }

    fun nameOf(node: NodeRef): String? {
        val id = (node as? NodeRef.OfSymbol)?.id ?: return null
        return (symbols + graph.symbols(SymbolQuery(limit = Int.MAX_VALUE))).firstOrNull { it.id == id }?.name
    }

    /** One line per binding: the name of the contract and what it was bound to. */
    fun bindings(): List<String> = relationships.filter { it.kind == RelationKind.BINDS }.map { relationship ->
        val outcome = when (val resolution = relationship.resolution) {
            is Resolution.Resolved -> "${nameOf(resolution.target)} ${resolution.certainty}"
            is Resolution.Unresolved -> "unresolved ${resolution.reason.value}"
            is Resolution.NotAnalysed -> "not-analysed ${resolution.reason.value}"
        }
        "${nameOf(relationship.from)} -> $outcome"
    }
}
