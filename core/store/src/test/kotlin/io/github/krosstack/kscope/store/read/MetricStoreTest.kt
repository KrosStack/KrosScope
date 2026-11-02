package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.HistorySignals
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore
import kotlin.test.Test
import kotlin.test.assertEquals

class MetricStoreTest {
    private val first = NodeRef.OfArtifact(Builders.artifactRef("src/First.kt"))
    private val second = NodeRef.OfArtifact(Builders.artifactRef("src/Second.kt"))

    private val metrics = listOf(
        Metric(first, HistorySignals.COMMITS, 12.0),
        Metric(first, HistorySignals.AUTHORS, 3.0),
        Metric(second, HistorySignals.COMMITS, 0.5),
        Metric(Builders.symbolRef("symbol"), "size.lines", 80.0),
    )

    private fun batchOf(owner: String, values: List<Metric>) = Builders.batch(owner, values.map { Fact.OfMetric(it) })

    @Test
    fun `metrics are read by node in canonical order`() = SqliteGraphStore.inMemory().use { store ->
        store.replace(batchOf("history", metrics))
        assertEquals(
            listOf(metrics[1], metrics[0]),
            store.metrics(first),
        )
        assertEquals(listOf(metrics[3]), store.metrics(Builders.symbolRef("symbol")))
        assertEquals(emptyList(), store.metrics(NodeRef.OfExternal("none")))
    }

    @Test
    fun `metrics are read by name across nodes`() = SqliteGraphStore.inMemory().use { store ->
        store.replace(batchOf("history", metrics))
        assertEquals(setOf(metrics[0], metrics[2]), store.metrics(HistorySignals.COMMITS).toSet())
        assertEquals(emptyList(), store.metrics("history.unknown"))
    }

    @Test
    fun `a value comes back exactly as it was stored`() = SqliteGraphStore.inMemory().use { store ->
        val exact = Metric(first, "ratio", 1.0 / 3.0)
        store.replace(batchOf("history", listOf(exact)))
        assertEquals(exact, store.metrics(first).single())
    }

    @Test
    fun `a replaced unit keeps its new metrics and a removed one none`() = SqliteGraphStore.inMemory().use { store ->
        store.replace(batchOf("history", metrics))
        store.replace(batchOf("history", listOf(metrics[0].copy(value = 13.0))))
        assertEquals(listOf(metrics[0].copy(value = 13.0)), store.metrics(first))
        assertEquals(emptyList(), store.metrics(second))
        store.remove(Builders.producer, OwnershipUnit(OwnershipKind.ARTIFACT, "history"))
        assertEquals(emptyList(), store.metrics(HistorySignals.COMMITS))
    }

    @Test
    fun `the same metric from two units is read once`() = SqliteGraphStore.inMemory().use { store ->
        store.replace(batchOf("one", listOf(metrics[0])))
        store.replace(batchOf("two", listOf(metrics[0])))
        assertEquals(listOf(metrics[0]), store.metrics(first).filter { it.name == HistorySignals.COMMITS })
    }
}
