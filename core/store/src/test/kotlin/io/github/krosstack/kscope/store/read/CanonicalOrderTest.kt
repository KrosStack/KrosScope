package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.store.SqliteGraphStore
import io.github.krosstack.kscope.store.generator.FactGenerator
import io.github.krosstack.kscope.store.replaceAll
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CanonicalOrderTest {
    @Test
    fun `the key of a fact is its canonical JSON`() {
        val facts = (0 until SEEDS).flatMap { seed -> FactGenerator(seed).batches(UNITS).flatMap { it.facts } }

        assertTrue(facts.size > MIN_FACTS)
        facts.forEach { assertEquals(KscopeJson.encodeToString<Fact>(it), CanonicalOrder.key(it)) }
    }

    @Test
    fun `values come out in the order that sorting their canonical JSON gives`() {
        repeat(SEEDS) { seed ->
            val facts = FactGenerator(seed).batches(UNITS).flatMap { it.facts }
            val relationships = facts.mapNotNull { (it as? Fact.OfRelationship)?.value }
            val symbols = facts.mapNotNull { (it as? Fact.OfSymbol)?.value }

            assertEquals(
                legacySorted(relationships, Fact::OfRelationship),
                CanonicalOrder.sorted(relationships, Fact::OfRelationship),
                "relationships, seed $seed",
            )
            assertEquals(
                legacySorted(symbols, Fact::OfSymbol),
                CanonicalOrder.sorted(symbols, Fact::OfSymbol),
                "symbols, seed $seed",
            )
        }
    }

    @Test
    fun `a store reads relationships and symbols in that same order`() {
        repeat(SEEDS) { seed ->
            val batches = FactGenerator(seed).batches(UNITS)
            SqliteGraphStore.inMemory().use { store ->
                store.replaceAll(batches)
                val relationships = store.relationships(RelationshipQuery(limit = Int.MAX_VALUE))
                val symbols = store.symbols(SymbolQuery(limit = Int.MAX_VALUE))

                assertEquals(legacySorted(relationships, Fact::OfRelationship), relationships, "seed $seed")
                assertEquals(legacySorted(symbols, Fact::OfSymbol), symbols, "seed $seed")
            }
        }
    }

    private fun <T : Any> legacySorted(values: List<T>, asFact: (T) -> Fact): List<T> =
        values.map { it to KscopeJson.encodeToString<Fact>(asFact(it)) }.sortedBy { it.second }.map { it.first }

    private companion object {
        const val SEEDS = 25
        const val UNITS = 6
        const val MIN_FACTS = 200
    }
}
