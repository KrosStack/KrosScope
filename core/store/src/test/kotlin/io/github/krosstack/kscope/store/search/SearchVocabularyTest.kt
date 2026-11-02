package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchVocabularyTest {
    private val index get() = store.searchIndex()
    private val store: GraphStore = SqliteGraphStore.inMemory().apply {
        addSymbols(
            "unit",
            searchSymbol("a", "SyncService").documented("Synchronizes the task list."),
            searchSymbol("b", "SyncWorker").documented("Runs a synchronization every hour."),
            searchSymbol("c", "TaskList").documented("Shows the tasks."),
            searchSymbol("d", "syncNow"),
        )
        addArtifacts(sourceArtifact("core/Settings.kt"))
        refreshSearchIndex(FakeContent(mapOf("core/Settings.kt" to "val zebra = 1")))
    }

    @AfterTest
    fun closeStore() = store.close()

    @Test
    fun `a word of the index is in the vocabulary and an absent word is not`() {
        assertTrue(index.vocabularyContains("task", SearchScope.SYMBOLS))
        assertFalse(index.vocabularyContains("elephant", SearchScope.SYMBOLS))
    }

    @Test
    fun `an inflected form of a word of the index is in the vocabulary`() {
        assertTrue(index.vocabularyContains("tasks", SearchScope.SYMBOLS))
        assertTrue(index.vocabularyContains("synchronizing", SearchScope.SYMBOLS))
    }

    @Test
    fun `the vocabulary of the symbols and the vocabulary of the artifacts are separate`() {
        assertTrue(index.vocabularyContains("zebra", SearchScope.ARTIFACTS))
        assertFalse(index.vocabularyContains("zebra", SearchScope.SYMBOLS))
        assertFalse(index.vocabularyContains("worker", SearchScope.ARTIFACTS))
    }

    @Test
    fun `terms with a prefix come with the most documents first and then alphabetically`() {
        val terms = index.vocabularyWithPrefix("sync", SearchScope.SYMBOLS)
        val frequencies = terms.map { index.documentFrequency(it, SearchScope.SYMBOLS) }
        assertEquals("sync", terms.first())
        assertTrue(terms.size > 1 && terms.all { it.startsWith("sync") }, "$terms")
        assertEquals(frequencies.sortedDescending(), frequencies)
        terms.zip(frequencies).groupBy({ it.second }, { it.first }).values.forEach { assertEquals(it.sorted(), it) }
    }

    @Test
    fun `the number of terms with a prefix is limited`() {
        assertEquals(1, index.vocabularyWithPrefix("sync", SearchScope.SYMBOLS, limit = 1).size)
        assertEquals(emptyList(), index.vocabularyWithPrefix("sync", SearchScope.SYMBOLS, limit = 0))
        assertEquals(emptyList(), index.vocabularyWithPrefix("qq", SearchScope.SYMBOLS))
        assertEquals(emptyList(), index.vocabularyWithPrefix("", SearchScope.SYMBOLS))
    }

    @Test
    fun `the document frequency counts the documents that hold a word or its stem`() {
        assertEquals(3, index.documentFrequency("sync", SearchScope.SYMBOLS))
        assertEquals(2, index.documentFrequency("task", SearchScope.SYMBOLS))
        assertEquals(2, index.documentFrequency("synchronize", SearchScope.SYMBOLS))
        assertEquals(0, index.documentFrequency("elephant", SearchScope.SYMBOLS))
        assertEquals(1, index.documentFrequency("zebra", SearchScope.ARTIFACTS))
    }

    @Test
    fun `a blank term is not in the vocabulary`() {
        assertFalse(index.vocabularyContains(" ", SearchScope.SYMBOLS))
        assertEquals(0, index.documentFrequency("", SearchScope.SYMBOLS))
    }
}
