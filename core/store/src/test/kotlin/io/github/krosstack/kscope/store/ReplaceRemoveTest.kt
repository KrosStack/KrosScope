package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SymbolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReplaceRemoveTest {
    private val first = Builders.symbols("unit-1", Builders.symbol("a"), Builders.symbol("b"))
    private val second = Builders.symbols("unit-2", Builders.symbol("c"))

    @Test
    fun `replace swaps out everything the unit owned before`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(first)
            val replacement = Builders.symbols("unit-1", Builders.symbol("z"))
                .copy(inputFingerprint = Fingerprint("new"))
            store.replace(replacement)

            assertNull(store.symbol(SymbolId("a")))
            assertNotNull(store.symbol(SymbolId("z")))
            assertEquals(listOf(replacement.stored()), store.units())
        }
    }

    @Test
    fun `replace leaves the facts of other units untouched`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(listOf(first, second))
            store.replace(Builders.symbols("unit-1"))

            assertNotNull(store.symbol(SymbolId("c")))
            assertNull(store.symbol(SymbolId("a")))
            assertEquals(2, store.units().size)
        }
    }

    @Test
    fun `the same owner under another producer or producer version is a separate unit`() {
        val otherProducer = first.copy(producer = ProducerRef("other", "1"))
        val otherVersion = first.copy(producer = ProducerRef(Builders.producer.id, "2"))
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(listOf(first, otherProducer, otherVersion))
            assertEquals(3, store.units().size)

            store.remove(otherProducer.producer, otherProducer.owner)
            assertEquals(2, store.units().size)
        }
    }

    @Test
    fun `remove deletes the unit with its facts`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(listOf(first, second))
            store.remove(first.producer, first.owner)

            assertEquals(listOf(second.stored()), store.units())
            assertNull(store.symbol(SymbolId("a")))
            assertEquals(canonicalJson(listOf(second)), canonicalJson(store.batches()))
        }
    }

    @Test
    fun `removing a unit that does not exist changes nothing`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(first)
            store.remove(ProducerRef("unknown", "1"), first.owner)
            store.remove(first.producer, OwnershipUnit(OwnershipKind.MODULE, "unknown"))

            assertEquals(listOf(first.stored()), store.units())
            assertNotNull(store.symbol(SymbolId("a")))
        }
    }

    @Test
    fun `units are listed in canonical order`() {
        val batches = listOf(
            Builders.symbols("b-unit", Builders.symbol("x")),
            Builders.symbols("a-unit", Builders.symbol("y")),
            Builders.symbols("a-unit", Builders.symbol("z")).copy(producer = ProducerRef("aaa", "1")),
        )
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(batches)
            assertEquals(listOf("aaa", "test-producer", "test-producer"), store.units().map { it.producer.id })
            assertEquals(listOf("a-unit", "a-unit", "b-unit"), store.units().map { it.owner.key })
        }
    }
}
