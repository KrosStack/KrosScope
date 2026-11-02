package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompilerCoverageCostsTest {
    @Test
    fun `compiler costs label the smallest successful tested budget despite nonmonotonic later selection`() {
        val measured = mapOf(
            TokenCount(2_000) to task(0.5, 1_900),
            TokenCount(4_000) to task(0.9, 3_800),
            TokenCount(8_000) to task(0.5, 7_000),
            TokenCount(16_000) to task(1.0, 9_000),
        )
        val annotated = CompilerCoverageCosts.annotate(measured, listOf(ContextBenchmarkMethod.FULL_COMPILER))
        val costs = annotated.getValue(TokenCount(2_000)).methods.getValue(ContextBenchmarkMethod.FULL_COMPILER)
            .fullFileCoverageCosts

        assertEquals(TokenCount(4_000), costs.testedBudget90)
        assertEquals(TokenCount(3_800), costs.tokens90)
        assertEquals(TokenCount(16_000), costs.testedBudget100)
        assertEquals(TokenCount(9_000), costs.tokens100)
        assertEquals(CoverageCostMeasurement.TESTED_GRID_UPPER_BOUND, costs.measurement90)
        assertTrue(costs.reason90.orEmpty().contains("not a global minimum"))
    }

    @Test
    fun `unreached compiler coverage reports the largest tested bound without inventing a cost`() {
        val annotated = CompilerCoverageCosts.annotate(
            mapOf(TokenCount(16_000) to task(0.5, 12_000)),
            listOf(ContextBenchmarkMethod.FULL_COMPILER),
        )
        val costs = annotated.values.single().methods.values.single().fullFileCoverageCosts

        assertNull(costs.tokens90)
        assertNull(costs.testedBudget90)
        assertEquals(CoverageCostMeasurement.NOT_REACHED, costs.measurement100)
        assertTrue(costs.reason100.orEmpty().contains("16000"))
    }

    private fun task(coverage: Double, tokens: Int): TaskContextResult {
        val scores = ContextScores(
            0.0, 0.0, null, coverage, 0.0, TokenCount(tokens), TokenCount(0),
            emptyList(), emptyList(), emptyList(),
        )
        val context = ContextPackage(
            "task",
            ContextLevel.RECOMMENDED,
            null,
            TokenizerId("test"),
            TokenCount(tokens),
            emptyList(),
        )
        val method = ContextMethodResult(scores, context, FullFileCoverageCosts())
        return TaskContextResult(
            "sample",
            "task",
            ContextGoldSpec(listOf("source")),
            mapOf(ContextBenchmarkMethod.FULL_COMPILER to method),
        )
    }
}
