package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Samples
import io.github.krosstack.kscope.store.generator.FactGenerator
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundTripTest {
    @Test
    fun `the sample batch with every fact type survives a round trip`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Samples.batch)
            assertEquals(canonicalJson(listOf(Samples.batch)), canonicalJson(store.batches()))
        }
    }

    @Test
    fun `arbitrary generated batches survive a round trip`() {
        repeat(SEEDS) { seed ->
            val batches = FactGenerator(seed).batches(UNITS_PER_SEED)
            SqliteGraphStore.inMemory().use { store ->
                store.replaceAll(batches)
                assertEquals(canonicalJson(batches), canonicalJson(store.batches()), "seed $seed")
            }
        }
    }

    @Test
    fun `the generator produces every fact type so the round trip is not vacuous`() {
        val facts = (0 until SEEDS).flatMap { seed -> FactGenerator(seed).batches(UNITS_PER_SEED).flatMap { it.facts } }
        val types = facts.map { it::class }.toSet()
        assertEquals(FACT_TYPES, types.size)
    }

    @Test
    fun `replacing a unit with generated content leaves exactly the new content`() {
        repeat(SEEDS) { seed ->
            val generator = FactGenerator(seed)
            val first = generator.batches(1).single()
            val second = generator.batch(first.owner).copy(producer = first.producer)
            SqliteGraphStore.inMemory().use { store ->
                store.replace(first)
                store.replace(second)
                assertEquals(canonicalJson(listOf(second)), canonicalJson(store.batches()), "seed $seed")
            }
        }
    }

    @Test
    fun `batches come back in canonical order whatever the insertion order`() {
        val batches = FactGenerator(1).batches(UNITS_PER_SEED)
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(batches.reversed())
            assertEquals(CanonicalFacts.sorted(batches), store.batches())
        }
    }

    @Test
    fun `a batch without facts still keeps its unit and fingerprint`() {
        val empty = Samples.batch.copy(facts = emptyList())
        SqliteGraphStore.inMemory().use { store ->
            store.replace(empty)
            assertEquals(listOf(empty), store.batches())
        }
    }

    @Test
    fun `reopening a file store reproduces the same content`() {
        val batches = FactGenerator(SEEDS).batches(UNITS_PER_SEED)
        TempDirectory().use { directory ->
            val file = directory.path.resolve("index/graph.db")
            val before = openedStore(file).use { store ->
                store.replaceAll(batches)
                canonicalJson(store.batches())
            }
            openedStore(file).use { store ->
                assertEquals(before, canonicalJson(store.batches()))
                assertEquals(canonicalJson(batches), before)
            }
        }
    }

    private companion object {
        const val SEEDS = 40
        const val UNITS_PER_SEED = 3
        const val FACT_TYPES = 12
    }
}
