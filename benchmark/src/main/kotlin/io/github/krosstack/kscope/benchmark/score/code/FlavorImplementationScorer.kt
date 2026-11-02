package io.github.krosstack.kscope.benchmark.score.code

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId

private const val VARIANT_ALTERNATIVE = "variant-alternative"
private val usageKinds = setOf(RelationKind.REFERENCES, RelationKind.CALLS, RelationKind.TYPE_USES)
private val inheritanceKinds = setOf(RelationKind.EXTENDS, RelationKind.IMPLEMENTS)

/** A pair that gold expects or the graph states: [node] reaches [implementation], found by [hop]. */
private class Reach(val node: NodeRef, val implementation: Symbol, val hop: Relationship)

/**
 * The flavor-specific implementations of one name: from the shared interface and from the shared usage site to the
 * implementation of each flavor. The compiler reaches the implementation of the default variant; the others are reached
 * through the `variant-alternative` relationships between the declarations of the flavors.
 */
internal class FlavorImplementationScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val alternatives: Map<SymbolId, List<Symbol>> = alternativesOf()

    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = reaches().flatMap { reach ->
        alternatives[reach.implementation.id].orEmpty().map { implementation ->
            val isReached = implementation.id == reach.implementation.id
            val hop = if (isReached) reach.hop else alternativeHop(reach, implementation)
            Item(pairKey(reach.node, NodeRef.OfSymbol(implementation.id)), implementation.name, evidenceOf(hop))
        }
    }.distinctBy { it.key }

    private fun alternativeHop(reach: Reach, implementation: Symbol): Relationship {
        val direct = index.relationships.firstOrNull { edge ->
            edge.subKind == VARIANT_ALTERNATIVE && connects(edge, reach.implementation.id, implementation.id)
        }
        return direct ?: reach.hop
    }

    private fun connects(edge: Relationship, first: SymbolId, second: SymbolId): Boolean {
        val ends = setOf(symbolIdOf(edge.from), symbolIdOf(index.resolvedTarget(edge)))
        return first in ends && second in ends
    }

    // An interface reaches its implementers, which the graph states the other way round; a usage site reaches what it
    // uses.
    private fun reaches(): List<Reach> = index.relationships.mapNotNull { edge ->
        val source = symbolIdOf(edge.from)?.let { index.symbolsById[it] }
        val target = symbolIdOf(index.resolvedTarget(edge))?.let { index.symbolsById[it] }
        if (source == null || target == null) null else reachOf(edge, source, target)
    }

    private fun reachOf(edge: Relationship, source: Symbol, target: Symbol): Reach? {
        val isImplementation = edge.kind in inheritanceKinds && source.id in alternatives
        val isUsage = edge.kind in usageKinds && target.id in alternatives && source.id !in alternatives
        return when {
            isImplementation -> Reach(NodeRef.OfSymbol(target.id), source, edge)
            isUsage -> Reach(edge.from, target, edge)
            else -> null
        }
    }

    private fun symbolIdOf(node: NodeRef?): SymbolId? = (node as? NodeRef.OfSymbol)?.id

    private fun alternativesOf(): Map<SymbolId, List<Symbol>> {
        val neighbours = index.relationships.filter { it.subKind == VARIANT_ALTERNATIVE }
            .mapNotNull { edge ->
                val from = symbolIdOf(edge.from)
                val to = symbolIdOf(index.resolvedTarget(edge))
                if (from != null && to != null) from to to else null
            }
            .flatMap { (from, to) -> listOf(from to to, to to from) }
            .groupBy({ it.first }, { it.second })
        return neighbours.keys.associateWith { start -> componentOf(start, neighbours) }
    }

    private fun componentOf(start: SymbolId, neighbours: Map<SymbolId, List<SymbolId>>): List<Symbol> {
        val seen = linkedSetOf(start)
        val pending = ArrayDeque(listOf(start))
        while (pending.isNotEmpty()) {
            neighbours[pending.removeFirst()].orEmpty().filter(seen::add).forEach(pending::addLast)
        }
        return seen.mapNotNull { index.symbolsById[it] }.filter { it.kind.category == SymbolCategory.TYPE }
    }

    private companion object {
        const val NAME = "flavor_specific_implementation"
    }
}
