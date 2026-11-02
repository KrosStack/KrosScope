package io.github.krosstack.kscope.store

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Samples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnitBatchTest {
    @Test
    fun `a stored unit is read back on its own in canonical order`() {
        val other = Builders.symbols("unit-2", Builders.symbol("c"))
        SqliteGraphStore.inMemory().use { store ->
            store.replaceAll(listOf(Samples.batch, other))
            val read = store.batch(Samples.batch.producer, Samples.batch.owner)
            assertEquals(CanonicalFacts.sorted(Samples.batch), read)
        }
    }

    @Test
    fun `an unknown unit has no batch`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(Samples.batch)
            assertNull(store.batch(Samples.batch.producer, Builders.symbols("missing").owner))
        }
    }
}
