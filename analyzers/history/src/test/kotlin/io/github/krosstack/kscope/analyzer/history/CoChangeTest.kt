package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoChangeTest {
    private fun repository(): SyntheticRepository = SyntheticRepository().also {
        it.commit("alice", "A.kt" to "1", "B.kt" to "1", "C.kt" to "1")
        it.commit("alice", "A.kt" to "2", "B.kt" to "2")
        it.commit("bob", "A.kt" to "3", "B.kt" to "3", "C.kt" to "3")
        it.commit("bob", "A.kt" to "4")
    }

    private fun Emitted.edge(from: String, to: String): Relationship? =
        relationshipsFrom(from).singleOrNull { (it.resolution as Resolution.Resolved).target == nodeAt(to) }

    private fun Emitted.supportOf(from: String, to: String): String? =
        edge(from, to)?.attributes?.get(HistorySignals.SUPPORT)

    private fun Emitted.confidenceOf(from: String, to: String): String? =
        edge(from, to)?.attributes?.get(HistorySignals.CONFIDENCE)

    @Test
    fun `files that changed together at least twice are related in both directions`() = repository().use { repository ->
        val emitted = repository.produce()
        assertEquals("3", emitted.supportOf("A.kt", "B.kt"))
        assertEquals("3", emitted.supportOf("B.kt", "A.kt"))
        assertEquals("2", emitted.supportOf("A.kt", "C.kt"))
        assertEquals("2", emitted.supportOf("C.kt", "A.kt"))
    }

    @Test
    fun `the confidence is the support divided by the commits of the file the relationship starts from`() =
        repository().use { repository ->
            val emitted = repository.produce()
            assertEquals("0.7500", emitted.confidenceOf("A.kt", "B.kt"))
            assertEquals("1.0000", emitted.confidenceOf("B.kt", "A.kt"))
            assertEquals("0.5000", emitted.confidenceOf("A.kt", "C.kt"))
            assertEquals("1.0000", emitted.confidenceOf("C.kt", "A.kt"))
        }

    @Test
    fun `a confidence is rounded to four decimals`() = SyntheticRepository().use { repository ->
        repeat(2) { repository.commit("alice", "A.kt" to "x$it", "B.kt" to "x$it") }
        repository.commit("alice", "A.kt" to "last")
        val emitted = repository.produce()
        assertEquals("0.6667", emitted.confidenceOf("A.kt", "B.kt"))
        assertEquals("1.0000", emitted.confidenceOf("B.kt", "A.kt"))
    }

    @Test
    fun `files that changed together once are not related`() = repository().use { repository ->
        repository.commit("carol", "D.kt" to "1", "A.kt" to "5")
        assertNull(repository.produce().supportOf("A.kt", "D.kt"))
    }

    @Test
    fun `a relationship is a resolved historical heuristic edge between two artifacts without a site`() =
        repository().use { repository ->
            val relationship = repository.produce().relationshipsFrom("A.kt").first()
            assertEquals(RelationKind.CO_CHANGES_WITH, relationship.kind)
            assertEquals(EvidenceClass.HISTORICAL, relationship.evidenceClass)
            assertEquals(Certainty.HEURISTIC, (relationship.resolution as Resolution.Resolved).certainty)
            assertNull(relationship.site)
            assertEquals(setOf(HistorySignals.SUPPORT, HistorySignals.CONFIDENCE), relationship.attributes.keys)
        }

    @Test
    fun `files that left the inventory are not related to anything`() = repository().use { repository ->
        repository.commit("dave", "B.kt" to null)
        val emitted = repository.produce()
        assertNull(emitted.supportOf("A.kt", "B.kt"))
        assertEquals("2", emitted.supportOf("A.kt", "C.kt"))
        assertTrue(emitted.relationships.none { it.from == nodeAt("B.kt") })
    }
}
