package io.github.krosstack.kscope.analyzer.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryScopeTest {
    private fun wide(prefix: String, count: Int): List<Pair<String, String?>> = List(count) { "$prefix$it.kt" to "1" }

    @Test
    fun `a commit that touches more files than the limit is skipped whole`() = SyntheticRepository().use { repository ->
        repository.commit("alice", "Core.kt" to "1", "Util.kt" to "1")
        val mass = wide("Wide", HistoryAnalysis.MAX_FILES_PER_COMMIT + 1) + listOf("Core.kt" to "2", "Util.kt" to "2")
        repository.commit("alice", mass)
        repository.commit("alice", "Core.kt" to "3", "Util.kt" to "3")
        val emitted = repository.produce()
        assertEquals(2.0, emitted.metricOf("Core.kt", COMMITS))
        assertNull(emitted.metricOf("Wide0.kt", COMMITS))
        assertEquals("2", emitted.relationshipsFrom("Core.kt").single().attributes["support"])
    }

    @Test
    fun `a commit at the limit still counts`() = SyntheticRepository().use { repository ->
        repository.commit("alice", wide("File", HistoryAnalysis.MAX_FILES_PER_COMMIT))
        repository.commit("alice", "File1.kt" to "2")
        assertEquals(2.0, repository.produce().metricOf("File1.kt", COMMITS))
    }

    @Test
    fun `a merge commit is not counted but the commits it merged are`() = SyntheticRepository().use { repository ->
        repository.commit("alice", "Main.kt" to "1", "Shared.kt" to "1")
        repository.git("checkout", "-q", "-b", "feature")
        repository.commit("bob", "Feature.kt" to "1", "Shared.kt" to "2")
        repository.git("checkout", "-q", "-")
        repository.commit("alice", "Main.kt" to "2")
        repository.git("merge", "-q", "--no-ff", "-m", "merge", "feature")
        val emitted = repository.produce()
        assertEquals(1.0, emitted.metricOf("Feature.kt", COMMITS))
        assertEquals(2.0, emitted.metricOf("Shared.kt", COMMITS))
        assertEquals(2.0, emitted.metricOf("Main.kt", COMMITS))
    }

    @Test
    fun `a workspace below the root of the work tree sees its own files under their own paths`() =
        SyntheticRepository().use { repository ->
            repository.commit("alice", "app/Core.kt" to "1", "app/Util.kt" to "1", "lib/Other.kt" to "1")
            repository.commit("bob", "app/Core.kt" to "2", "app/Util.kt" to "2", "lib/Other.kt" to "2")
            val emitted = repository.produce(repository.directory.resolve("app"))
            assertEquals(2.0, emitted.metricOf("Core.kt", COMMITS))
            assertNull(emitted.metricOf("app/Core.kt", COMMITS))
            assertNull(emitted.metricOf("Other.kt", COMMITS))
            assertEquals(2, emitted.relationships.size)
            assertTrue(emitted.relationships.all { it.from == nodeAt("Core.kt") || it.from == nodeAt("Util.kt") })
        }

    @Test
    fun `files outside the workspace count toward the limit of a commit`() = SyntheticRepository().use { repository ->
        repository.commit("alice", "app/Core.kt" to "1")
        repository.commit("alice", wide("lib/Lib", HistoryAnalysis.MAX_FILES_PER_COMMIT + 1) + ("app/Core.kt" to "2"))
        val emitted = repository.produce(repository.directory.resolve("app"))
        assertEquals(1.0, emitted.metricOf("Core.kt", COMMITS))
    }
}
