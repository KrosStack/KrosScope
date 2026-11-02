package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SymbolSearchRankingTest {
    private val tasks = TaskTideSymbols

    @Test
    fun `a name outranks a documentation sentence that holds the same word`() = tasks.indexed(
        searchSymbol("queue", "WorkQueue", path = "q/WorkQueue.kt").documented("Orders the work by priority."),
        searchSymbol("priority", "Priority", path = "p/Priority.kt"),
    ).use { store ->
        val hits = store.symbolHits("priority")
        val ids = hits.symbolIds()
        assertTrue(ids.indexOf("priority") < ids.indexOf("queue"), "$ids")
        assertContains(hits[ids.indexOf("queue")].fieldScores, "documentation")
        assertContains(hits[ids.indexOf("priority")].fieldScores, "name")
    }

    @Test
    fun `symbols that score alike are ordered by their node key`() = SqliteGraphStore.inMemory().use { store ->
        store.addSymbols("unit", searchSymbol("b-widget", "Widget"), searchSymbol("c-widget", "Widget"))
        store.addSymbols("other", searchSymbol("a-widget", "Widget"))
        store.refreshed()
        assertEquals(listOf("a-widget", "b-widget", "c-widget"), store.symbolHits("widget").symbolIds())
        assertEquals(store.symbolHits("widget"), store.symbolHits("widget"))
    }

    @Test
    fun `the fields of a hit add up to its score`() = tasks.indexed().use { store ->
        val hits = store.symbolHits("task", "repository", "sync")
        assertTrue(hits.isNotEmpty())
        hits.forEach { hit -> assertEquals(hit.score, hit.fieldScores.values.sum(), TOLERANCE) }
    }

    @Test
    fun `the weight of a term scales its contribution`() = tasks.indexed().use { store ->
        val plain = store.symbolHits("repository").first()
        val query = SearchQuery(listOf(SearchTerm("repository", weight = 2.5)), SearchScope.SYMBOLS)
        val weighted = store.searchIndex().search(query).first()
        assertEquals(plain.node, weighted.node)
        assertEquals(plain.score * 2.5, weighted.score, TOLERANCE)
    }

    @Test
    fun `a hit lists the terms that found it`() = tasks.indexed().use { store ->
        val hits = store.symbolHits("repository", "server", "nonexistent")
        val matched = hits.symbolIds().zip(hits.map { it.matchedTerms }).toMap()
        assertEquals(listOf("repository", "server"), matched.getValue("repository"))
        assertEquals(listOf("repository"), matched.getValue("sync-tasks"))
    }

    @Test
    fun `the limit cuts the best hits`() = tasks.indexed().use { store ->
        assertEquals(store.symbolHits("task").take(2), store.symbolHits("task", limit = 2))
    }

    @Test
    fun `blank terms and a limit of zero find nothing`() = tasks.indexed().use { store ->
        assertEquals(emptyList(), store.symbolHits("  "))
        assertEquals(emptyList(), store.symbolHits("task", limit = 0))
        assertEquals(emptyList(), store.searchIndex().search(SearchQuery(emptyList(), SearchScope.SYMBOLS)))
    }

    @Test
    fun `terms made of punctuation or of query syntax do not fail`() = tasks.indexed().use { store ->
        listOf("!!!", "*", "\"", "\"\"", "AND", "NEAR(task", "task OR", "-task", "task:", "^task").forEach { text ->
            val hits = store.symbolHits(text)
            assertTrue(hits.all { it.score > 0.0 }, "$text gave $hits")
        }
        assertEquals(emptyList(), store.symbolHits("!!!"))
        assertEquals(emptyList(), store.symbolHits("\"\""))
    }

    @Test
    fun `many hits all get their field scores`() = SqliteGraphStore.inMemory().use { store ->
        store.replace(Builders.symbolBatch("many", List(MANY) { searchSymbol("widget-$it", "Widget$it") }))
        store.refreshed()
        val hits = store.symbolHits("widget", limit = MANY * 2)
        assertEquals(MANY, hits.size)
        assertTrue(hits.all { it.fieldScores.isNotEmpty() })
    }

    private companion object {
        const val MANY = 1_200
        const val TOLERANCE = 1e-9
    }
}
