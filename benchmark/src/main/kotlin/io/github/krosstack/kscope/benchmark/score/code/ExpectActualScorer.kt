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

/**
 * Every top-level `expect` declaration to each of its `actual` declarations. The graph states the edge from the
 * `actual` to the `expect`, so it is turned around; edges of members are left out because gold covers a class with its
 * members.
 */
internal class ExpectActualScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) }
        val matching = Matching.of(expected + mapped.unmappedMisses(), produced())
        return scoreOf(NAME, gold, mapped, matching)
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.ACTUALIZES }
        .mapNotNull { relationship ->
            val expect = index.resolvedTarget(relationship) as? NodeRef.OfSymbol
            val isTopLevel = expect != null && index.symbolsById[expect.id]?.container == null
            if (expect == null || !isTopLevel) null else itemOf(expect, relationship)
        }

    private fun itemOf(expect: NodeRef, relationship: Relationship) =
        Item(pairKey(expect, relationship.from), "actualizes", evidenceOf(relationship))

    private companion object {
        const val NAME = "expect_actual"
    }
}
