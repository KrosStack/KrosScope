package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.benchmark.stats.ComparisonMetric
import io.github.krosstack.kscope.benchmark.stats.ComparisonOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalizationBenchmarkTest {
    private val baseline = LocalizationScores(0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2)
    private val improved = LocalizationScores(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4)

    @Test
    fun `tier one requires strict improvement over both raw text baselines in each metric`() {
        val tiedScores = listOf(
            improved.copy(recall5 = baseline.recall5),
            improved.copy(recall10 = baseline.recall10),
            improved.copy(ndcg10 = baseline.ndcg10),
        )

        for (scores in tiedScores) {
            val aggregate = LocalizationBenchmark.aggregate(listOf(result(scores = scores)))
            assertTrue(aggregate.valid)
            assertFalse(aggregate.gatePassed, "A tie must fail: $scores")
        }
        assertTrue(LocalizationBenchmark.aggregate(listOf(result())).gatePassed)
        for (method in listOf(BenchmarkMethod.BM25_RAW, BenchmarkMethod.BM25_ENGLISH)) {
            val task = result().withScore(method, improved.copy(ndcg10 = 0.5))
            assertFalse(LocalizationBenchmark.aggregate(listOf(task)).gatePassed, method.name)
        }
    }

    @Test
    fun `lexical only is compared with paired intervals without blocking tier one`() {
        val task = result().withScore(BenchmarkMethod.LEXICAL, improved.copy(recall5 = 0.5))
        val aggregate = LocalizationBenchmark.aggregate(listOf(task))

        assertTrue(aggregate.gatePassed)
        assertEquals(LocalizationGateScores(0.2, 0.2, 0.2), aggregate.strongestBm25)
        assertEquals(3, aggregate.tier2Comparisons.size)
        val recall5 = aggregate.tier2Comparisons.first { it.metric == ComparisonMetric.RECALL_5 }
        assertEquals(ComparisonOutcome.SIGNIFICANT_LOSS, recall5.outcome)
        assertEquals(-0.1, recall5.pointDelta ?: error("Missing paired delta"), 1e-12)
    }

    @Test
    fun `the strongest lexical comparison takes each metric independently`() {
        val task = result()
            .withScore(BenchmarkMethod.BM25_RAW, baseline.copy(recall5 = 0.7))
            .withScore(BenchmarkMethod.BM25_ENGLISH, baseline.copy(recall10 = 0.8))
            .withScore(BenchmarkMethod.LEXICAL, baseline.copy(ndcg10 = 0.9))
        val aggregate = LocalizationBenchmark.aggregate(listOf(task))

        assertEquals(LocalizationGateScores(0.7, 0.8, 0.9), aggregate.strongestLexical)
        assertEquals(LocalizationGateScores(0.7, 0.8, 0.2), aggregate.strongestBm25)
        assertFalse(aggregate.gatePassed)
    }

    @Test
    fun `the primary gate measures full retrieval separately from the category ablation`() {
        val categoryWin = result(scores = baseline).withScore(BenchmarkMethod.CATEGORY, improved)
        val fullWin = result().withScore(BenchmarkMethod.CATEGORY, baseline)

        assertFalse(LocalizationBenchmark.aggregate(listOf(categoryWin)).gatePassed)
        assertTrue(LocalizationBenchmark.aggregate(listOf(fullWin)).gatePassed)
    }

    @Test
    fun `empty duplicated and missing gold corpora cannot pass even with improving scores`() {
        val invalidCorpora = listOf(
            emptyList(),
            listOf(result(), result()),
            listOf(result().copy(missingGoldFiles = listOf("absent"))),
            listOf(result().copy(validGold = false)),
        )

        for (tasks in invalidCorpora) {
            val aggregate = LocalizationBenchmark.aggregate(tasks)
            assertFalse(aggregate.valid)
            assertFalse(aggregate.gatePassed)
        }
    }

    @Test
    fun `gate uses aggregate scores while preserving individual task losses`() {
        val loss = result("loss", baseline.copy(recall5 = 0.1, recall10 = 0.1, ndcg10 = 0.1))
        val win = result("win", improved)
        val aggregate = LocalizationBenchmark.aggregate(listOf(loss, win))

        assertTrue(aggregate.gatePassed)
        assertEquals(0.25, aggregate.methods.getValue(BenchmarkMethod.FULL).recall5)
        assertEquals(0.25, aggregate.methods.getValue(BenchmarkMethod.FULL).recall10)
        assertEquals(0.25, aggregate.methods.getValue(BenchmarkMethod.FULL).ndcg10)
        assertEquals(listOf(loss, win), aggregate.tasks)
    }

    @Test
    fun `a suite cannot pass with an absent held out set or a failing set`() {
        val original = LocalizationBenchmark.aggregate(listOf(result()))
        val heldOut = LocalizationBenchmark.aggregate(listOf(result()), BenchmarkSet.HELD_OUT)
        val failed = LocalizationBenchmark.aggregate(listOf(result(scores = baseline)), BenchmarkSet.HELD_OUT)

        assertFalse(LocalizationBenchmark.combine(listOf(original)).complete)
        assertFalse(LocalizationBenchmark.combine(listOf(original)).gatePassed)
        assertFalse(LocalizationBenchmark.combine(listOf(original, failed)).gatePassed)
        assertTrue(LocalizationBenchmark.combine(listOf(original, heldOut)).gatePassed)
    }

    @Test
    fun `supplemental targets require both test metrics and symbol recall without redefining the primary gate`() {
        val scores = improved.copy(
            analysis = LocalizationAnalysisScores(symbolRecall10 = 0.5, testPrecision = 0.5, testRecall = 0.8),
        )
        val passing = LocalizationBenchmark.aggregate(listOf(result(scores = scores)))
        assertTrue(passing.gatePassed)
        assertTrue(passing.testSelectionPassed)
        assertTrue(passing.symbolRecallPassed)

        val lowPrecision = scores.copy(analysis = scores.analysis?.copy(testPrecision = 0.49))
        val lowRecall = scores.copy(analysis = scores.analysis?.copy(testRecall = 0.79))
        for (score in listOf(lowPrecision, lowRecall)) {
            val failing = LocalizationBenchmark.aggregate(listOf(result(scores = score)))
            assertTrue(failing.gatePassed)
            assertFalse(failing.testSelectionPassed)
        }
        val lowSymbols = scores.copy(analysis = scores.analysis?.copy(symbolRecall10 = 0.49))
        assertFalse(LocalizationBenchmark.aggregate(listOf(result(scores = lowSymbols))).symbolRecallPassed)
    }

    private fun result(id: String = "sample", scores: LocalizationScores = improved): TaskLocalization {
        val methods = BenchmarkMethod.entries.associateWith { method ->
            val methodScores = if (method == BenchmarkMethod.FULL) scores else baseline
            LocalizationMethodResult(methodScores, emptyList(), emptyList())
        }
        return TaskLocalization(id, "change", methods, emptyList())
    }

    private fun TaskLocalization.withScore(method: BenchmarkMethod, scores: LocalizationScores): TaskLocalization =
        copy(methods = methods + (method to methods.getValue(method).copy(scores = scores)))
}
