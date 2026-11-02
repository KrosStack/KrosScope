package io.github.krosstack.kscope.analyzer.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HistoryMetricsTest {
    private fun repository(): SyntheticRepository = SyntheticRepository().also {
        it.commit("alice", "Core.kt" to "v1", "Util.kt" to "v1")
        it.commit("bob", "Core.kt" to "v2")
        it.commit("alice", "Core.kt" to "v3", "Api.kt" to "v1")
        it.commit("carol", "Util.kt" to "v2")
    }

    @Test
    fun `each file has the number of commits that changed it`() = repository().use { repository ->
        val emitted = repository.produce()
        assertEquals(3.0, emitted.metricOf("Core.kt", COMMITS))
        assertEquals(2.0, emitted.metricOf("Util.kt", COMMITS))
        assertEquals(1.0, emitted.metricOf("Api.kt", COMMITS))
    }

    @Test
    fun `each file has the number of distinct authors that changed it`() = repository().use { repository ->
        val emitted = repository.produce()
        assertEquals(2.0, emitted.metricOf("Core.kt", AUTHORS))
        assertEquals(2.0, emitted.metricOf("Util.kt", AUTHORS))
        assertEquals(1.0, emitted.metricOf("Api.kt", AUTHORS))
    }

    @Test
    fun `the file changed last has rank zero and files of one commit share a rank`() = repository().use { repository ->
        val emitted = repository.produce()
        assertEquals(0.0, emitted.metricOf("Util.kt", RECENCY))
        assertEquals(1.0, emitted.metricOf("Core.kt", RECENCY))
        assertEquals(1.0, emitted.metricOf("Api.kt", RECENCY))
    }

    @Test
    fun `the recency rank counts newer commits and never holds a time`() = SyntheticRepository().use { repository ->
        repository.commit("alice", "Old.kt" to "v1")
        repeat(RANKS) { repository.commit("alice", "Middle$it.kt" to "v1") }
        repository.commit("alice", "New.kt" to "v1")
        val emitted = repository.produce()
        assertEquals(0.0, emitted.metricOf("New.kt", RECENCY))
        assertEquals((RANKS + 1).toDouble(), emitted.metricOf("Old.kt", RECENCY))
    }

    @Test
    fun `a file that is not in the inventory has no metrics however long its history`() =
        repository().use { repository ->
            val emitted = produceHistory(setOf("Core.kt"), repository.directory, repository.head())
            assertEquals(setOf(nodeAt("Core.kt")), emitted.metrics.map { it.node }.toSet())
            assertNull(emitted.metricOf("Util.kt", COMMITS))
        }

    @Test
    fun `a file without any commit has no metrics`() = repository().use { repository ->
        repository.write("Untracked.kt", "v1")
        assertNull(repository.produce().metricOf("Untracked.kt", COMMITS))
    }

    @Test
    fun `a deleted file has no metrics`() = repository().use { repository ->
        repository.commit("dave", "Util.kt" to null)
        val emitted = repository.produce()
        assertNull(emitted.metricOf("Util.kt", COMMITS))
        assertEquals(3.0, emitted.metricOf("Core.kt", COMMITS))
    }

    @Test
    fun `authors are counted by the e-mail of the commit, whatever a mailmap says`() = repository().use { repository ->
        val before = repository.produce().metricOf("Core.kt", AUTHORS)
        repository.write(".mailmap", "alice <alice@example.invalid> <bob@example.invalid>\n")
        assertEquals(before, repository.produce().metricOf("Core.kt", AUTHORS))
    }

    private companion object {
        const val RANKS = 3
    }
}
