package io.github.krosstack.kscope.store.search

import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope
import io.github.krosstack.kscope.spi.search.SearchTerm
import io.github.krosstack.kscope.store.Builders
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SymbolSearchMatchingTest {
    private val tasks = TaskTideSymbols

    @Test
    fun `literal contract metadata contributes searchable identifier parts`() = tasks.indexed(
        searchSymbol(
            "receiver",
            "Receiver",
        ).copy(
            attributes = mapOf("actions" to "android.intent.action.BOOT_COMPLETED"),
        ),
    ).use { store ->
        val hit = store.symbolHits("completed").single()
        assertEquals(listOf("receiver"), listOf(hit).symbolIds())
        assertContains(hit.fieldScores, "metadata_parts")
    }

    @Test
    fun `a part of an identifier finds the symbol that is named after it`() = tasks.indexed().use { store ->
        val ids = store.symbolHits("repository").symbolIds()
        assertEquals("repository", ids.first())
        assertContains(ids, "sync-tasks")
    }

    @Test
    fun `the whole identifier finds the symbol through its name field`() = tasks.indexed().use { store ->
        val hit = store.symbolHits("taskrepository").first()
        assertEquals(listOf("repository"), listOf(hit).symbolIds())
        assertContains(hit.fieldScores.keys, "name")
    }

    @Test
    fun `snake case and camel case spellings of one name meet`() = tasks.indexed(
        searchSymbol("camel", "priorityLabel", kotlinFunction, path = "ui/Labels.kt"),
    ).use { store ->
        assertEquals(setOf("label", "camel"), store.symbolHits("prioritylabel").symbolIds().toSet())
    }

    @Test
    fun `an inflected word finds the words that share its stem`() = tasks.indexed().use { store ->
        assertContains(store.symbolHits("syncs").symbolIds(), "repository")
        assertContains(store.symbolHits("synchronize").symbolIds(), "service")
        assertContains(store.symbolHits("task").symbolIds(), "service")
    }

    @Test
    fun `a prefix term matches the words it starts and a plain term does not`() = tasks.indexed().use { store ->
        assertEquals(emptyList(), store.symbolHits("synch").symbolIds())
        val prefix = SearchQuery(listOf(SearchTerm("synch", prefix = true)), SearchScope.SYMBOLS)
        assertEquals(listOf("service"), store.searchIndex().search(prefix).symbolIds())
    }

    @Test
    fun `generated symbols are indexed and library symbols are not`() = tasks.indexed(
        searchSymbol("generated", "priority_label", path = null, origin = SymbolOrigin.Generated("r", null)),
        searchSymbol("library", "SyncHelper", origin = SymbolOrigin.Library("org.example:lib")),
    ).use { store ->
        assertContains(store.symbolHits("prioritylabel").symbolIds(), "generated")
        assertEquals(emptyList(), store.symbolHits("synchelper").symbolIds())
    }

    @Test
    fun `a symbol declared by several units has one document`() = tasks.indexed().use { store ->
        store.addSymbols("second", tasks.syncTasks)
        store.refreshed()
        assertEquals(1, store.symbolHits("synctasks").size)
        assertEquals(1, store.searchIndex().documentFrequency("synctasks", SearchScope.SYMBOLS))
    }

    @Test
    fun `a symbol whose unit is removed leaves the index`() = tasks.indexed().use { store ->
        store.remove(Builders.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "unit"))
        store.refreshed()
        assertEquals(emptyList(), store.symbolHits("task"))
    }
}
