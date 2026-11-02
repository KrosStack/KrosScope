package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.read.SymbolFixtures.alpha
import io.github.krosstack.kscope.store.read.SymbolFixtures.ids
import io.github.krosstack.kscope.store.read.SymbolFixtures.withSymbols
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SymbolLookupTest {
    @Test
    fun `a symbol is found by id with every field intact`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.symbols("unit", Samples.symbol))
            assertEquals(Samples.symbol, store.symbol(Samples.symbol.id))
        }
    }

    @Test
    fun `an unknown symbol id yields nothing`() = withSymbols { store ->
        assertNull(store.symbol(SymbolId("missing")))
    }

    @Test
    fun `text that looks like SQL or a pattern is matched literally`() {
        val tricky = Builders.symbol("tricky", name = "x' OR '1'='1")
        val wildcard = Builders.symbol("wild", name = "50%")
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.symbols("unit", tricky, wildcard, alpha))
            assertEquals(listOf("tricky"), store.ids(SymbolQuery(name = "x' OR '1'='1")))
            assertEquals(listOf("wild"), store.ids(SymbolQuery(name = "50%")))
        }
    }

    @Test
    fun `symbols come back in the canonical order of the model whatever the insertion order`() {
        val symbols = List(SYMBOL_COUNT) { Builders.symbol("id ${SYMBOL_COUNT - it}", name = "n${it % 3}") }
        val batch = Builders.symbolBatch("unit", symbols)
        val expected = CanonicalFacts.sorted(batch).facts.map { (it as Fact.OfSymbol).value }
        SqliteGraphStore.inMemory().use { store ->
            store.replace(batch)
            assertEquals<List<Symbol>>(expected, store.symbols(SymbolQuery()))
        }
    }

    private companion object {
        const val SYMBOL_COUNT = 25
    }
}
