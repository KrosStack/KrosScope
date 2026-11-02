package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.model.context.TokenizerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextReportTest {
    private fun result(budgets: List<TokenCount>, tier1Passed: Boolean): ContextBenchmarkResult {
        val measured = budgets.map {
            ContextBudgetResult(it, emptyList(), emptyMap(), true, emptyList(), valid = true)
        }
        return ContextBenchmarkResult(
            sets = BenchmarkSet.entries.map { ContextSetResult(it, measured) },
            estimator = TokenizerId("o200k_base"),
            corpus = LocalizationCorpusSummary(),
            definition = ContextMeasurementDefinition(),
            tier1Passed = tier1Passed,
            complete = true,
            valid = true,
        )
    }

    @Test
    fun `a run restricted to some budgets says that it is not a Tier 1 evaluation`() {
        val report = ContextReport.markdown(result(listOf(TokenCount(2_000)), tier1Passed = true))

        assertTrue(report.contains("Tier 1: NOT EVALUATED."), report)
        assertTrue(report.contains("A partial-budget run is not a Tier 1 evaluation"), report)
        assertTrue(report.contains("Budgets measured: 2000."), report)
        assertFalse(report.contains("Tier 1: PASS"), report)
        assertFalse(report.lineSequence().any { it == "Tier 1: PASS. Valid: true." }, report)
    }

    @Test
    fun `a run of all four budgets reports the Tier 1 verdict as before`() {
        val passed = ContextReport.markdown(result(ContextBenchmarkBudgets.values, tier1Passed = true))
        val failed = ContextReport.markdown(result(ContextBenchmarkBudgets.values, tier1Passed = false))

        assertTrue(passed.contains("Tier 1: PASS. Both sets measured: true."), passed)
        assertTrue(passed.contains("### Budget 16000\n\nTier 1: PASS. Valid: true."), passed)
        assertTrue(failed.contains("Tier 1: FAIL. Both sets measured: true."), failed)
        assertFalse(passed.contains("NOT EVALUATED"), passed)
    }
}
