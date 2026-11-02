package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RetrievalIndex
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.retrieval.TaskRetrieval
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.relationship
import io.github.krosstack.kscope.spi.search.SearchHit
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GraphRankingTest {
    @Test
    fun `unresolved and not analysed relationships never propagate ranking`() {
        val root = node("Root.kt")
        val unresolved = relationship(root, node("Missing.kt")).copy(
            resolution = Resolution.Unresolved("Missing", ReasonCode("test.missing")),
        )
        val notAnalysed = unresolved.copy(
            resolution = Resolution.NotAnalysed("Unknown", ReasonCode("test.unsupported")),
        )
        val graph = RetrievalGraph(RetrievalFixture(edges = listOf(unresolved, notAnalysed)))

        assertEquals(mapOf(root to 1.0), GraphRanking(graph).rank(mapOf(root to 1.0)))
    }

    @Test
    fun `graph ranks stronger evidence above weaker evidence and stops after three hops`() {
        val root = node("Root.kt")
        val exact = node("Exact.kt")
        val probable = node("Probable.kt")
        val heuristic = node("Heuristic.kt")
        val chain = (1..4).map { node("Chain$it.kt") }
        val edges = listOf(
            relationship(root, exact),
            relationship(root, probable, certainty = Certainty.PROBABLE),
            relationship(root, heuristic, certainty = Certainty.HEURISTIC),
            relationship(root, chain.first()),
        ) + chain.zipWithNext().map { (from, to) -> relationship(from, to) }
        val graph = RetrievalGraph(RetrievalFixture(edges = edges))
        val scores = GraphRanking(graph).rank(mapOf(root to 1.0))

        assertTrue(scores.getValue(exact) > scores.getValue(probable))
        assertTrue(scores.getValue(probable) > scores.getValue(heuristic))
        assertTrue(chain[2] in scores)
        assertTrue(chain[3] !in scores)
        assertEquals(1.0, scores.values.sum(), 0.0000001)
    }

    @Test
    fun `seed insertion order does not change graph scores`() {
        val first = node("First.kt")
        val second = node("Second.kt")
        val graph = RetrievalGraph(RetrievalFixture(edges = listOf(relationship(first, second))))
        val ranking = GraphRanking(graph)

        assertEquals(
            ranking.rank(linkedMapOf(first to 1.0, second to 2.0)),
            ranking.rank(linkedMapOf(second to 2.0, first to 1.0)),
        )
    }

    @Test
    fun `generated file relevance projects to its editable source of truth`() {
        val source = artifact("schema/Model.proto")
        val generated = artifact("generated/Model.kt").copy(
            generated = GeneratedInfo("test.generator", NodeRef.OfArtifact(source.ref)),
        )
        val hit = SearchHit(NodeRef.OfArtifact(generated.ref), 1.0, listOf("model"), emptyMap())
        val retrieval = TaskRetrieval(RetrievalFixture(listOf(source, generated)), RetrievalIndex(hits = listOf(hit)))
        val result = retrieval.analyze("model")

        assertEquals(listOf(source.ref), result.files.map { it.artifact })
        assertEquals(FileRole.EDIT, result.files.single().role)
        assertTrue(result.files.single().reasons.any { it.signal == SignalKind.LEXICAL_ARTIFACT })
        assertEquals(result, retrieval.analyze("model"))
    }

    @Test
    fun `zero confidence historical edges do not expand the neighborhood`() {
        val root = node("Root.kt")
        val edge = relationship(root, node("Other.kt"), RelationKind.CO_CHANGES_WITH).copy(
            attributes = mapOf("confidence" to "0.0000"),
        )
        val graph = RetrievalGraph(RetrievalFixture(edges = listOf(edge)))

        assertEquals(setOf(root), GraphRanking(graph).rank(mapOf(root to 1.0)).keys)
    }
}
