package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSymbolTest {
    @Test
    fun `a symbol owned by two units is returned once and prefers the source origin`() {
        val generated = Builders.symbol("shared", name = "generated", origin = SymbolOrigin.Synthetic("plugin"))
        val source = Builders.symbol("shared", name = "source")
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.symbols("unit-a", generated))
            store.replace(Builders.symbols("unit-b", source))

            assertEquals(source, store.symbol(SymbolId("shared")))
            assertEquals(listOf(source), store.symbols(SymbolQuery()))

            store.remove(Builders.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "unit-b"))
            assertEquals(generated, store.symbol(SymbolId("shared")))
        }
    }

    @Test
    fun `a symbol owned by two units with the same origin resolves to the canonically first`() {
        val early = Builders.symbol("shared", name = "a-name")
        val late = Builders.symbol("shared", name = "b-name")
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Builders.symbols("unit-a", late))
            store.replace(Builders.symbols("unit-b", early))
            assertEquals(early, store.symbol(SymbolId("shared")))
        }
    }
}
