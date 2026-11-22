package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.spi.search.SearchScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchCapTest {
    private val capped = TwoRepositories(extraLibrary = CROWD, mostCandidates = CAP)

    @Test
    fun `an analysis says that its search reached the cap when the repository was not found before it`() {
        val inside = capped.retrieval.analyze("task", within = APP)

        assertTrue(inside.searchCapped)
        assertTrue(inside.files.isEmpty() && inside.symbols.isEmpty())
        assertEquals(CAP, capped.index.limits(SearchScope.SYMBOLS).last())
    }

    @Test
    fun `a symbol search says that its search reached the cap`() {
        assertTrue(capped.retrieval.symbol("task", within = APP).searchCapped)
    }

    @Test
    fun `the note of the cap survives the fusion of file rankings`() {
        val fused = capped.retrieval.withFileRanking(capped.retrieval.analyze("task", within = APP), emptyList())

        assertTrue(fused.searchCapped)
    }

    @Test
    fun `a search that fills its limits before the cap is not capped`() {
        assertFalse(capped.retrieval.analyze("task", within = LIB).searchCapped)
        assertFalse(capped.retrieval.symbol("task", within = LIB).searchCapped)
    }

    @Test
    fun `a search that reads every hit of the index before the cap is not capped`() {
        val roomy = TwoRepositories(extraLibrary = CROWD)

        assertFalse(roomy.retrieval.analyze("task", within = RepositoryId("none")).searchCapped)
        assertFalse(roomy.retrieval.symbol("task", within = RepositoryId("none")).searchCapped)
    }

    @Test
    fun `a search without a repository is never capped`() {
        assertFalse(capped.retrieval.analyze("task").searchCapped)
        assertFalse(capped.retrieval.symbol("task").searchCapped)
    }

    private companion object {
        const val CROWD = 300
        const val CAP = 200
    }
}
