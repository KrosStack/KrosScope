package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.stats.ComparisonOutcome
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextAggregatesTest {
    @Test
    fun `path and lexical wins remain diagnostic while both raw text baselines define tier one`() {
        val result = ContextAggregates.budget(
            TokenCount(2_000),
            listOf(task()),
            ContextBenchmarkOptions.DEFAULT_METHODS,
        )

        assertTrue(result.valid)
        assertTrue(result.tier1Passed)
        assertTrue(result.tier2Comparisons.all { it.outcome == ComparisonOutcome.SIGNIFICANT_LOSS })
    }

    @Test
    fun `a tie in recall or precision against either raw baseline fails tier one`() {
        for (method in listOf(ContextBenchmarkMethod.BM25_RAW, ContextBenchmarkMethod.BM25_ENGLISH)) {
            val original = task()
            val baseline = original.methods.getValue(method)
            val tied = listOf(baseline.scores.copy(goldContextRecall = 0.8), baseline.scores.copy(tokenPrecision = 0.8))
            tied.forEach { score ->
                val task = original.copy(methods = original.methods + (method to baseline.copy(scores = score)))
                val result = ContextAggregates.budget(
                    TokenCount(2_000),
                    listOf(task),
                    ContextBenchmarkOptions.DEFAULT_METHODS,
                )
                assertFalse(result.tier1Passed)
            }
        }
    }

    private fun task(): TaskContextResult {
        val methods = ContextBenchmarkOptions.DEFAULT_METHODS.associateWith { method ->
            val value = when (method) {
                ContextBenchmarkMethod.FULL_COMPILER -> 0.8
                ContextBenchmarkMethod.BM25_RAW, ContextBenchmarkMethod.BM25_ENGLISH -> 0.6
                else -> 1.0
            }
            val scores = ContextScores(
                value, value, null, value, value, TokenCount(0), TokenCount(0),
                emptyList(), emptyList(), emptyList(),
            )
            val context = ContextPackage(
                "task",
                ContextLevel.RECOMMENDED,
                TokenCount(2_000),
                TokenizerId("test"),
                TokenCount(0),
                emptyList(),
            )
            ContextMethodResult(scores, context, FullFileCoverageCosts())
        }
        return TaskContextResult("sample", "task", ContextGoldSpec(listOf("source")), methods)
    }
}
