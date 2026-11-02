package io.github.krosstack.kscope.model

import kotlinx.serialization.SerializationException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MetricTest {
    private val node = NodeRef.OfArtifact(Samples.artifactRef)

    private fun encoded(value: Double) =
        KscopeJson.encodeToString(Metric.serializer(), Metric(node, HistorySignals.COMMITS, value))

    @Test
    fun `a metric is written under its own discriminator with its fields in declaration order`() {
        val encoded = KscopeJson.encodeToString(Fact.serializer(), Fact.OfMetric(Samples.metric))
        assertContains(encoded, "{\"type\":\"metric\",\"value\":{\"node\":{\"type\":\"artifact\"")
        assertContains(encoded, "\"name\":\"history.commits\",\"value\":12}")
    }

    @Test
    fun `integral values are written without a fraction or an exponent`() {
        assertContains(encoded(12.0), "\"value\":12}")
        assertContains(encoded(0.0), "\"value\":0}")
        assertContains(encoded(-0.0), "\"value\":0}")
        assertContains(encoded(1.0E10), "\"value\":10000000000}")
        assertContains(encoded(-3.0), "\"value\":-3}")
    }

    @Test
    fun `fractional values are written in plain decimal notation`() {
        assertContains(encoded(0.5), "\"value\":0.5}")
        assertContains(encoded(1.0E-5), "\"value\":0.00001}")
        assertContains(encoded(2.50), "\"value\":2.5}")
    }

    @Test
    fun `any finite value survives the round trip exactly`() {
        val random = Random(SEED)
        val values = List(SAMPLES) { Double.fromBits(random.nextLong()) }.filter { it.isFinite() && it != 0.0 }
        values.forEach { value ->
            val metric = Metric(node, HistorySignals.AUTHORS, value)
            val decoded = KscopeJson.decodeFromString(Metric.serializer(), KscopeJson.encodeToString(metric))
            assertEquals(metric, decoded)
        }
    }

    @Test
    fun `equal values are written as the same bytes whatever their notation`() {
        assertEquals(encoded(3.0), encoded(3.000))
        assertEquals(encoded(0.1 + 0.2), encoded(0.30000000000000004))
    }

    @Test
    fun `a value that is not finite is rejected`() {
        assertFailsWith<IllegalArgumentException> { Metric(node, "x", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { Metric(node, "x", Double.POSITIVE_INFINITY) }
        val encodedNaN = "{\"node\":${nodeJson()},\"name\":\"x\",\"value\":\"NaN\"}"
        assertFailsWith<SerializationException> { KscopeJson.decodeFromString(Metric.serializer(), encodedNaN) }
    }

    @Test
    fun `metrics in a batch are ordered by their canonical form whatever the input order`() {
        val metrics = listOf(
            Metric(node, HistorySignals.COMMITS, 2.0),
            Metric(node, HistorySignals.AUTHORS, 1.0),
            Metric(NodeRef.OfModule(Samples.moduleId), HistorySignals.COMMITS, 7.0),
        )
        val forward = CanonicalFacts.sorted(Samples.batch.copy(facts = metrics.map { Fact.OfMetric(it) }))
        val backward = CanonicalFacts.sorted(Samples.batch.copy(facts = metrics.reversed().map { Fact.OfMetric(it) }))
        assertEquals(forward, backward)
    }

    private fun nodeJson() = KscopeJson.encodeToString(NodeRef.serializer(), node)

    private companion object {
        const val SEED = 20_260_605
        const val SAMPLES = 2_000
    }
}
