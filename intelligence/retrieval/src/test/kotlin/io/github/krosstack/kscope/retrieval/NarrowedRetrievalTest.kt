package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.search.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NarrowedRetrievalTest {
    private val repositories = TwoRepositories()

    @Test
    fun `a task analysis within a repository ranks only files and symbols of that repository`() {
        val everywhere = repositories.retrieval.analyze("task store")
        val inside = repositories.retrieval.analyze("task store", within = APP)

        assertTrue(everywhere.files.any { it.artifact.repository == LIB })
        assertTrue(everywhere.symbols.any { it.symbol.declaration?.artifact?.repository == LIB })
        assertTrue(inside.files.isNotEmpty() && inside.symbols.isNotEmpty())
        assertTrue(inside.files.all { it.artifact.repository == APP })
        assertTrue(inside.symbols.all { it.symbol.declaration?.artifact?.repository == APP })
    }

    @Test
    fun `a hit that was generated from a symbol of another repository brings nothing of that repository`() {
        val inside = repositories.retrieval.analyze("task", within = APP)

        assertTrue(repositories.sync.id !in inside.symbols.map { it.symbol.id })
        assertTrue(repositories.libSync.ref !in inside.files.map { it.artifact })
    }

    @Test
    fun `an exact mention of a file of another repository is not ranked within a repository`() {
        val everywhere = repositories.retrieval.analyze("change TaskSync.kt")
        val inside = repositories.retrieval.analyze("change TaskSync.kt", within = APP)

        assertTrue(repositories.libSync.ref in everywhere.files.map { it.artifact })
        assertTrue(inside.files.none { it.artifact.repository == LIB })
    }

    @Test
    fun `the limits are filled from inside the repository when the other one has better hits`() {
        val crowded = TwoRepositories(extraLibrary = CROWD)
        val everywhere = crowded.retrieval.analyze("task")
        val inside = crowded.retrieval.analyze("task", within = APP)

        assertTrue(everywhere.symbols.none { it.symbol.declaration?.artifact?.repository == APP })
        assertTrue(everywhere.files.none { it.artifact.repository == APP })
        assertTrue(crowded.store.id in inside.symbols.map { it.symbol.id })
        assertTrue(crowded.reminders.id in inside.symbols.map { it.symbol.id })
        assertTrue(crowded.appStore.ref in inside.files.map { it.artifact })
        assertTrue(crowded.appReminders.ref in inside.files.map { it.artifact })
    }

    @Test
    fun `more hits are asked for until the limits are filled and not once more`() {
        val crowded = TwoRepositories(extraLibrary = CROWD)

        crowded.retrieval.analyze("task", within = APP)

        val symbols = crowded.index.limits(SearchScope.SYMBOLS)
        val artifacts = crowded.index.limits(SearchScope.ARTIFACTS)
        assertEquals(SYMBOL_LIMIT, symbols.first())
        assertEquals(ARTIFACT_CANDIDATES, artifacts.first())
        assertTrue(symbols.zipWithNext().all { (before, after) -> after > before })
        assertTrue(artifacts.zipWithNext().all { (before, after) -> after > before })
        assertTrue(symbols.last() > CROWD)
        assertTrue(artifacts.last() > CROWD)
    }

    @Test
    fun `a repository that matches nothing ends the search once the index has no more hits`() {
        val crowded = TwoRepositories(extraLibrary = CROWD)

        val inside = crowded.retrieval.analyze("task", within = RepositoryId("none"))

        assertTrue(inside.files.isEmpty() && inside.symbols.isEmpty())
        assertTrue(crowded.index.limits(SearchScope.SYMBOLS).size < MOST_ASKS)
    }

    @Test
    fun `a symbol search within a repository drops the matches of other repositories and keeps the exact match`() {
        val everywhere = repositories.retrieval.symbol("TaskSync")
        val inside = repositories.retrieval.symbol("TaskSync", within = APP)

        assertTrue(repositories.clock.id in everywhere.symbols.map { it.symbol.id })
        assertEquals(
            setOf(repositories.sync.id, repositories.store.id, repositories.reminders.id),
            inside.symbols.map { it.symbol.id }.toSet(),
        )
        assertEquals(repositories.sync.id, inside.symbols.first().symbol.id)
    }

    @Test
    fun `a symbol search within a repository fills its limit from inside it`() {
        val crowded = TwoRepositories(extraLibrary = CROWD)

        val inside = crowded.retrieval.symbol("task", within = APP)

        assertTrue(crowded.store.id in inside.symbols.map { it.symbol.id })
        assertTrue(inside.symbols.all { it.symbol.declaration?.artifact?.repository == APP })
    }

    private companion object {
        const val CROWD = 120
        const val SYMBOL_LIMIT = 50
        const val ARTIFACT_CANDIDATES = 100
        const val MOST_ASKS = 10
    }
}
