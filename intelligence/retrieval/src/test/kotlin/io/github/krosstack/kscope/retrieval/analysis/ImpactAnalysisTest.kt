package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.RetrievalFixture
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.artifact
import io.github.krosstack.kscope.retrieval.node
import io.github.krosstack.kscope.retrieval.relationship
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ImpactAnalysisTest {
    @Test
    fun `impact follows callers transitively and preserves weakest path certainty`() {
        val root = node("Root.kt")
        val caller = node("Caller.kt")
        val consumer = node("Consumer.kt")
        val dependency = node("Dependency.kt")
        val first = relationship(caller, root, certainty = Certainty.HEURISTIC)
        val second = relationship(consumer, caller)
        val edges = listOf(first, second, relationship(root, dependency))
        val graph = RetrievalGraph(RetrievalFixture(listOf(artifact("Root.kt")), edges))
        val result = ImpactAnalysis(graph).analyze("Root.kt")

        assertEquals(listOf(caller, consumer), result.affected.map { it.node })
        assertEquals(listOf(1, 2), result.affected.map { it.distance })
        assertEquals(listOf("heuristic", "heuristic"), result.affected.map { it.certainty })
        assertEquals(listOf(first, second), result.affected.last().path)
        assertTrue(result.affected.none { it.node == dependency })
    }

    @Test
    fun `impact follows generated surfaces to their callers and reports unresolved boundaries`() {
        val source = node("Contract.aidl")
        val generated = node("Generated.kt")
        val caller = node("Caller.kt")
        val unresolved = relationship(caller, node("Missing.kt")).copy(
            resolution = Resolution.Unresolved("Missing", ReasonCode("test.missing")),
        )
        val edges = listOf(
            relationship(source, generated, RelationKind.GENERATES),
            relationship(caller, generated),
            unresolved,
        )
        val graph = RetrievalGraph(RetrievalFixture(listOf(artifact("Contract.aidl")), edges))
        val result = ImpactAnalysis(graph).analyze("Contract.aidl")

        assertEquals(listOf(generated, caller), result.affected.map { it.node })
        assertEquals(listOf(unresolved), result.unresolved)
    }

    @Test
    fun `cycles terminate and missing roots yield no invented impact`() {
        val root = node("Root.kt")
        val caller = node("Caller.kt")
        val edges = listOf(relationship(root, caller), relationship(caller, root))
        val graph = RetrievalGraph(RetrievalFixture(listOf(artifact("Root.kt")), edges))
        val impact = ImpactAnalysis(graph)

        assertEquals(listOf(caller), impact.analyze("Root.kt").affected.map { it.node })
        assertTrue(impact.analyze("Absent.kt").affected.isEmpty())
        assertTrue(impact.analyze("Absent.kt").roots.isEmpty())
    }
}
