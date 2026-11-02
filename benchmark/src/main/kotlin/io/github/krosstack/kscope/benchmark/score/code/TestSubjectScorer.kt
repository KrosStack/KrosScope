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

/**
 * Each test class to the class, or the function, that it tests. The graph states a `TESTS` relationship from the test
 * class to its subject; its evidence class is that of the relationship.
 */
internal class TestSubjectScorer(private val index: GraphIndex, private val resolver: GoldAddressResolver) {
    fun score(gold: List<GoldRelationship>): ClassScore {
        val mapped = MappedClass.of(gold, resolver)
        val expected = mapped.expectedItems { pairKey(it.from, it.to) } + mapped.unmappedMisses()
        return scoreOf(NAME, gold, mapped, Matching.of(expected, produced()))
    }

    private fun produced(): List<Item> = index.relationships
        .filter { it.kind == RelationKind.TESTS }
        .mapNotNull { relationship ->
            val subject = index.resolvedTarget(relationship)?.takeIf { it is NodeRef.OfSymbol }
            subject?.let { Item(pairKey(relationship.from, it), "tests", evidenceOf(relationship)) }
        }

    private companion object {
        const val NAME = "test_to_subject"
    }
}
