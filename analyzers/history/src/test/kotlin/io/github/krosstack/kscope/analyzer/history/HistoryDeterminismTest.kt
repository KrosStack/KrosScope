package io.github.krosstack.kscope.analyzer.history

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.NodeRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryDeterminismTest {
    private fun repository(): SyntheticRepository = SyntheticRepository().also {
        it.commit("alice", "A.kt" to "1", "B.kt" to "1", "C.kt" to "1")
        it.commit("bob", "A.kt" to "2", "B.kt" to "2")
        it.commit("carol", "B.kt" to "3", "C.kt" to "2", "D.kt" to "1")
        it.commit("alice", "A.kt" to "3", "B.kt" to "4", "C.kt" to "3")
    }

    @Test
    fun `two runs give identical batches`() = repository().use { repository ->
        val first = repository.produce()
        val second = repository.produce()
        assertEquals(first.batches, second.batches)
        assertTrue(first.metrics.isNotEmpty() && first.relationships.isNotEmpty())
    }

    @Test
    fun `the order of the inventory does not change the facts`() = repository().use { repository ->
        val files = repository.files().toList()
        val forward = produceHistory(files, repository.directory, repository.head())
        val backward = produceHistory(files.reversed(), repository.directory, repository.head())
        assertEquals(CanonicalFacts.toJson(forward.batches), CanonicalFacts.toJson(backward.batches))
        assertEquals(forward.facts, backward.facts)
    }

    @Test
    fun `the facts come in the order of the paths`() = repository().use { repository ->
        val paths = repository.produce().metrics.map { (it.node as NodeRef.OfArtifact).ref.path.value }
        assertEquals(paths.sorted(), paths)
    }

    @Test
    fun `the batch belongs to the repository unit of the producer and names the revision`() =
        repository().use { repository ->
            val batch = repository.produce().batches.single()
            assertEquals("kscope.history.git", batch.producer.id)
            assertEquals(repository.head(), batch.inputFingerprint.value)
        }
}
