package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.search.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals

class UnnarrowedRetrievalTest {
    @Test
    fun `a task analysis without a repository gives the output it gave before narrowing existed`() {
        val result = TwoRepositories().retrieval.analyze("task store")

        assertEquals(resource("/unnarrowed/analysis.json"), KscopeJson.encodeToString(result))
    }

    @Test
    fun `a symbol search without a repository gives the output it gave before narrowing existed`() {
        val result = TwoRepositories().retrieval.symbol("task")

        assertEquals(resource("/unnarrowed/symbol.json"), KscopeJson.encodeToString(result))
    }

    @Test
    fun `an analysis without a repository asks the index for the same numbers of hits as before`() {
        val repositories = TwoRepositories(extraLibrary = CROWD)

        repositories.retrieval.analyze("task store")

        assertEquals(listOf(SYMBOL_LIMIT), repositories.index.limits(SearchScope.SYMBOLS))
        assertEquals(listOf(ARTIFACT_CANDIDATES), repositories.index.limits(SearchScope.ARTIFACTS))
    }

    @Test
    fun `a symbol search without a repository asks the index once`() {
        val repositories = TwoRepositories(extraLibrary = CROWD)

        repositories.retrieval.symbol("task")

        assertEquals(listOf(SYMBOL_LIMIT), repositories.index.limits(SearchScope.SYMBOLS))
        assertEquals(emptyList(), repositories.index.limits(SearchScope.ARTIFACTS))
    }

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResource(name)) { "Missing $name" }.readText()

    private companion object {
        const val CROWD = 60
        const val SYMBOL_LIMIT = 50
        const val ARTIFACT_CANDIDATES = 100
    }
}
