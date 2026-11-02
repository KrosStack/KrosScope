package io.github.krosstack.kscope.benchmark.stats

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairedBootstrapTest {
    @Test
    fun `paired constant differences keep covariance and produce an exact positive interval`() {
        val pairs = listOf(PairedValue("b", 0.3, 0.2), PairedValue("a", 0.9, 0.8))
        val result = PairedBootstrap.compare(ComparisonMetric.RECALL_5, pairs)

        assertTrue(result.valid)
        assertEquals(0.1, result.pointDelta ?: error("Missing delta"), 1e-12)
        assertEquals(0.1, result.lower95 ?: error("Missing interval"), 1e-12)
        assertEquals(0.1, result.upper95 ?: error("Missing interval"), 1e-12)
        assertTrue(result.significant)
        assertEquals(ComparisonOutcome.SIGNIFICANT_WIN, result.outcome)
        assertEquals(10_000, result.settings.resamples)
        assertEquals(listOf("a", "b"), result.pairs.map { it.taskId })
    }

    @Test
    fun `two paired differences have hand calculated percentile endpoints`() {
        val pairs = listOf(PairedValue("a", 0.8, 0.6), PairedValue("b", 0.7, 0.6))
        val result = PairedBootstrap.compare(ComparisonMetric.NDCG_10, pairs)

        assertEquals(0.15, result.pointDelta ?: error("Missing delta"), 1e-12)
        assertEquals(0.1, result.lower95 ?: error("Missing interval"), 1e-12)
        assertEquals(0.2, result.upper95 ?: error("Missing interval"), 1e-12)
        assertEquals(result, PairedBootstrap.compare(ComparisonMetric.NDCG_10, pairs.reversed()))
    }

    @Test
    fun `negative and zero intervals distinguish a loss from absence of significant evidence`() {
        val loss = PairedBootstrap.compare(ComparisonMetric.TOKEN_PRECISION, listOf(PairedValue("a", 0.1, 0.3)))
        val tied = PairedBootstrap.compare(ComparisonMetric.TOKEN_PRECISION, listOf(PairedValue("a", 0.3, 0.3)))
        val mixed = PairedBootstrap.compare(
            ComparisonMetric.GOLD_CONTEXT_RECALL,
            listOf(PairedValue("a", 1.0, 0.0), PairedValue("b", 0.0, 1.0)),
        )

        assertTrue(loss.significant)
        assertEquals(ComparisonOutcome.SIGNIFICANT_LOSS, loss.outcome)
        assertFalse(tied.significant)
        assertEquals(ComparisonOutcome.INCONCLUSIVE, tied.outcome)
        assertFalse(mixed.significant)
        assertEquals(0.0, mixed.pointDelta)
    }

    @Test
    fun `empty duplicate and nonfinite paired inputs remain unmeasured`() {
        val invalid = listOf(
            emptyList(),
            listOf(PairedValue("a", 1.0, 0.0), PairedValue("a", 0.0, 1.0)),
            listOf(PairedValue("a", Double.NaN, 0.0)),
            listOf(PairedValue("a", 1.0, Double.POSITIVE_INFINITY)),
        )

        invalid.forEach { pairs ->
            val result = PairedBootstrap.compare(ComparisonMetric.RECALL_10, pairs)
            assertFalse(result.valid)
            assertNull(result.pointDelta)
            assertNull(result.lower95)
            assertNull(result.upper95)
            assertEquals(ComparisonOutcome.UNMEASURED, result.outcome)
        }
    }

    @Test
    fun `linear percentiles interpolate between adjacent ordered resamples`() {
        assertEquals(7.5, BootstrapPercentiles.value(doubleArrayOf(0.0, 10.0, 20.0, 30.0), 0.25))
        assertEquals(22.5, BootstrapPercentiles.value(doubleArrayOf(0.0, 10.0, 20.0, 30.0), 0.75))
    }
}
