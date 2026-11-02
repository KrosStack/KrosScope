package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.spi.search.SearchHit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexicalSeedsTest {
    private val first = NodeRef.OfSymbol(SymbolId("first"))
    private val second = NodeRef.OfSymbol(SymbolId("second"))

    @Test
    fun `incomparable corpus score scales do not change restarted graph seed weights`() {
        val symbols = listOf(hit(first, 100.0), hit(second, 50.0))
        val artifacts = listOf(hit(node("source"), 1.0))
        val scaled = symbols.map { it.copy(score = it.score * 1000.0) }

        assertEquals(LexicalSeeds.weights(symbols, artifacts), LexicalSeeds.weights(scaled, artifacts))
    }

    @Test
    fun `larger candidate corpora do not consume the other corpus restart budget`() {
        val artifacts = listOf(hit(node("source"), 1.0))
        val graph = GraphRanking(RetrievalGraph(RetrievalFixture()))
        val small = graph.rank(LexicalSeeds.weights(listOf(hit(first, 1.0)), artifacts))
        val large = graph.rank(LexicalSeeds.weights(listOf(hit(first, 1.0), hit(second, 0.5)), artifacts))

        assertEquals(small.getValue(node("source")), large.getValue(node("source")))
        assertTrue(large.getValue(first) > large.getValue(second))
    }

    private fun hit(node: NodeRef, score: Double) = SearchHit(node, score, emptyList(), emptyMap())
}
