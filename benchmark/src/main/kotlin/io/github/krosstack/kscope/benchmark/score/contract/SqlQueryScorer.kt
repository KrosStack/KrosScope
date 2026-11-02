package io.github.krosstack.kscope.benchmark.score.contract

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.graph.GoldAddressResolver
import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.benchmark.graph.key
import io.github.krosstack.kscope.benchmark.score.ClassScore
import io.github.krosstack.kscope.benchmark.score.GeneratedSurfaces
import io.github.krosstack.kscope.benchmark.score.Item
import io.github.krosstack.kscope.benchmark.score.MappedClass
import io.github.krosstack.kscope.benchmark.score.Matching
import io.github.krosstack.kscope.benchmark.score.evidenceOf
import io.github.krosstack.kscope.benchmark.score.expectedItems
import io.github.krosstack.kscope.benchmark.score.pairKey
import io.github.krosstack.kscope.benchmark.score.scoreOf
import io.github.krosstack.kscope.benchmark.score.unmappedMisses
import io.github.krosstack.kscope.model.NodeRef

/**
 * Each query of a `.sq` file to each function that calls it. A call is a relationship from the calling declaration to
 * the generated function; the query is the source of truth the `GENERATES` relationship of that function names.
 */
internal class SqlQueryScorer(index: GraphIndex, private val resolver: GoldAddressResolver) {
    private val surfaces = GeneratedSurfaces(index)

    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = surfaces.uses.flatMap { use ->
        surfaces.sourcesOf(use.target, QUERY_KINDS).map { source ->
            val key = pairKey(NodeRef.OfSymbol(source.source.id), use.relationship.from)
            Item(key, "${source.source.name} <- ${use.path}", evidenceOf(source.hop))
        }
    }.distinctBy { it.key }

    private companion object {
        const val NAME = "sq_query_to_consumer"
        val QUERY_KINDS = setOf("sqldelight.query")
    }
}
