package io.github.krosstack.kscope.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionTest {
    private val first = Builders.symbols("unit-1", Builders.symbol("a"), Builders.symbol("b"))
    private val second = Builders.symbols("unit-2", Builders.symbol("c"))

    @Test
    fun `a duplicate symbol id in one batch is rejected by naming the id and changes nothing`() {
        SqliteGraphStore.inMemory().use { store ->
            store.replace(first)
            val duplicated = Builders.symbols("unit-1", Builders.symbol("dup"), Builders.symbol("dup"))

            val failure = assertFailsWith<IllegalArgumentException> { store.replace(duplicated) }

            assertTrue("dup" in failure.message.orEmpty(), failure.message)
            assertEquals(canonicalJson(listOf(first)), canonicalJson(store.batches()))
        }
    }

    @Test
    fun `a failing transaction rolls back every write made inside it`() {
        SqliteGraphStore.inMemory().use { store ->
            assertFailsWith<IllegalStateException> {
                store.transaction {
                    store.replace(first)
                    store.replace(second)
                    error("abort")
                }
            }
            assertTrue(store.units().isEmpty())
            assertTrue(store.batches().isEmpty())
        }
    }

    @Test
    fun `a transaction commits all its writes together`() {
        SqliteGraphStore.inMemory().use { store ->
            val result = store.transaction {
                store.replace(first)
                store.replace(second)
                store.units().size
            }
            assertEquals(2, result)
            assertEquals(2, store.units().size)
        }
    }

    @Test
    fun `a caught failure inside a nested transaction rolls back only the nested writes`() {
        SqliteGraphStore.inMemory().use { store ->
            store.transaction {
                store.replace(first)
                assertFailsWith<IllegalStateException> {
                    store.transaction {
                        store.replace(second)
                        error("abort nested")
                    }
                }
            }
            assertEquals(listOf(first.stored()), store.units())
        }
    }

    @Test
    fun `committed writes are durable in a file store`() {
        TempDirectory().use { directory ->
            val file = directory.path.resolve("graph.db")
            openedStore(file).use { store -> store.transaction { store.replace(first) } }
            openedStore(file).use { store -> assertEquals(listOf(first.stored()), store.units()) }
        }
    }

    @Test
    fun `an outer write discards a read snapshot predating an external commit`() {
        TempDirectory().use { directory ->
            val file = directory.path.resolve("graph.db")
            openedStore(file).use { reader ->
                reader.replace(first)
                assertEquals(listOf(first.stored()), reader.units())
                val replacement = Builders.symbols("unit-1", Builders.symbol("updated"))
                openedStore(file).use { writer -> writer.replace(replacement) }

                reader.replace(second)

                assertEquals(canonicalJson(listOf(replacement, second)), canonicalJson(reader.batches()))
            }
        }
    }
}
