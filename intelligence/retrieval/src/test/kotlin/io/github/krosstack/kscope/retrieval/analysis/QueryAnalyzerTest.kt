package io.github.krosstack.kscope.retrieval.analysis

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.retrieval.QueryExpansion
import io.github.krosstack.kscope.retrieval.RetrievalIndex
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class QueryAnalyzerTest {
    @Test
    fun `query terms retain domain words that are not standard English stop words`() {
        val query = QueryAnalyzer(RetrievalIndex()).analyze("users want an app with translation stored locally")

        listOf("users", "want", "app", "translation", "stored", "locally").forEach {
            assertContains(query.terms, it)
        }
        assertEquals(emptyList(), query.hints)
    }

    @Test
    fun `artifact hints require literal taxonomy terms rather than substrings`() {
        val analyzer = QueryAnalyzer(RetrievalIndex())

        assertEquals(emptyList(), analyzer.analyze("manifestation labelled colour translation").hints)
        assertEquals(listOf(SymbolCategory.COMPONENT), analyzer.analyze("Android manifest").hints)
    }

    @Test
    fun `quoted and qualified identifiers retain exact mentions`() {
        val query = QueryAnalyzer(RetrievalIndex()).analyze("Update `TaskRepository` in sample.TaskStore")

        assertEquals(listOf("TaskRepository", "sample.TaskStore"), query.exactMentions)
        assertContains(query.terms, "update")
        assertTrue(listOf("task", "repository", "store").all { it in query.terms })
    }

    @Test
    fun `unknown spelling bridges before abbreviation and expansions have reduced weight`() {
        val query = QueryAnalyzer(RetrievalIndex(setOf("synchronization", "sync"))).analyze("synchronisation")

        assertEquals(listOf(QueryExpansion("synchronisation", "synchronization", "spelling")), query.expansions)
        val terms = QueryAnalyzer.searchTerms(query)
        assertTrue(terms.last().weight > 0.0)
        assertTrue(terms.last().weight < terms.first().weight)
    }

    @Test
    fun `known words do not expand and unknown abbreviations bridge to vocabulary`() {
        val query = QueryAnalyzer(RetrievalIndex(setOf("sync", "repository"))).analyze("sync repo")

        assertEquals(listOf(QueryExpansion("repo", "repository", "abbreviation")), query.expansions)
    }

    @Test
    fun `prefix completion and category hints are deterministic`() {
        val index = RetrievalIndex(setOf("dispatcher", "dispatching", "dispatchable"))
        val query = QueryAnalyzer(index).analyze("dispatch Android string resource SQLDelight schema")

        assertEquals(listOf("dispatchable", "dispatcher", "dispatching"), query.expansions.map { it.replacement })
        assertEquals(listOf(SymbolCategory.RESOURCE, SymbolCategory.SCHEMA), query.hints)
        assertEquals(query, QueryAnalyzer(index).analyze(query.text))
    }
}
