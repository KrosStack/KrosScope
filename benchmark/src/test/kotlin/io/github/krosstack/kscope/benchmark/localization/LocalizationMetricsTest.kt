package io.github.krosstack.kscope.benchmark.localization

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationMetricsTest {
    @Test
    fun `ranking metrics use gold files and discounted positions independently of the edit selection`() {
        val task = LocalizationTask("sample", "change", listOf("a", "b"), listOf("context"))
        val score = LocalizationMetrics.score(
            listOf("miss", "context", "a", "b"),
            task,
            listOf("a", "miss"),
        )

        assertEquals(0.0, score.recall1)
        assertEquals(0.5, score.recall3)
        assertEquals(1.0, score.recall5)
        assertEquals(1.0, score.recall10)
        assertEquals(0.5706417189553201, score.ndcg10, 1e-12)
        assertEquals(5.0 / 12.0, score.averagePrecision, 1e-12)
        assertEquals(0.5, score.editPrecision)
        assertEquals(1.0, score.contextRecall10)
    }

    @Test
    fun `duplicate predictions and overlapping read gold do not inflate metrics`() {
        val task = LocalizationTask("sample", "change", listOf("a", "a", "b"), listOf("a", "context"))
        val score = LocalizationMetrics.score(listOf("a", "a", "miss"), task, listOf("a", "a", "miss"))

        assertEquals(0.5, score.recall1)
        assertEquals(0.5, score.recall10)
        assertEquals(0.6131471927654584, score.ndcg10, 1e-12)
        assertEquals(0.5, score.averagePrecision)
        assertEquals(0.5, score.editPrecision)
        assertEquals(1.0 / 3.0, score.contextRecall10)
    }

    @Test
    fun `a gold file at rank eleven contributes to average precision but not top ten scores`() {
        val task = LocalizationTask("sample", "change", listOf("gold"))
        val ranked = (1..10).map { "miss-$it" } + "gold"
        val score = LocalizationMetrics.score(ranked, task, emptyList())

        assertEquals(0.0, score.recall10)
        assertEquals(0.0, score.ndcg10)
        assertEquals(1.0 / 11.0, score.averagePrecision)
        assertEquals(0.0, score.editPrecision)
        assertEquals(0.0, score.contextRecall10)
    }

    @Test
    fun `empty gold and empty aggregate produce finite zero scores`() {
        val zero = LocalizationScores(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val empty = LocalizationTask("empty", "", emptyList())
        assertEquals(zero, LocalizationMetrics.score(listOf("miss"), empty, emptyList()))
        assertEquals(zero, LocalizationMetrics.mean(emptyList()))
    }

    @Test
    fun `aggregation gives each task equal weight regardless of gold size`() {
        val one = LocalizationTask("one", "", listOf("a"))
        val two = LocalizationTask("two", "", listOf("b", "c"))
        val perfect = LocalizationMetrics.score(listOf("a"), one, listOf("a"))
        val missed = LocalizationMetrics.score(emptyList(), two, emptyList())

        assertEquals(
            LocalizationScores(0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5),
            LocalizationMetrics.mean(listOf(perfect, missed)),
        )
    }
}
