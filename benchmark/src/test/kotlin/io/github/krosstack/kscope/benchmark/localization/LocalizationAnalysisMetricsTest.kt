package io.github.krosstack.kscope.benchmark.localization

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalizationAnalysisMetricsTest {
    private val structure = WorkspaceStructure(
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
    )
    private val suggested = RankedFile(
        ArtifactRef(RepositoryId("sample"), RepoPath("tests/SubjectTest.kt")),
        1.0,
        FileRole.READ,
        emptyList(),
    )

    @Test
    fun `explicitly empty test gold penalizes unwanted selected tests`() {
        val task = LocalizationTask("task", "change", listOf("source"), relevantTests = emptyList())
        val scores = LocalizationAnalysisMetrics.score(result(listOf(suggested)), task, structure)

        assertEquals(0.0, scores.testPrecision)
        assertEquals(1.0, scores.testRecall)
    }

    @Test
    fun `absent test gold stays unmeasured`() {
        val task = LocalizationTask("task", "change", listOf("source"))
        val scores = LocalizationAnalysisMetrics.score(result(listOf(suggested)), task, structure)

        assertNull(scores.testPrecision)
        assertNull(scores.testRecall)
    }

    @Test
    fun `empty selection correctly satisfies explicit empty test gold`() {
        val task = LocalizationTask("task", "change", listOf("source"), relevantTests = emptyList())
        val scores = LocalizationAnalysisMetrics.score(result(emptyList()), task, structure)

        assertEquals(1.0, scores.testPrecision)
        assertEquals(1.0, scores.testRecall)
    }

    @Test
    fun `scope dimensions independently report precision recall and exact match`() {
        val score = LocalizationSetMetrics.score(setOf("a", "b", "extra"), setOf("a", "b", "missing", "other"))

        assertEquals(2.0 / 3.0, score.precision)
        assertEquals(0.5, score.recall)
        assertEquals(0.0, score.exactMatch)
    }

    @Test
    fun `explicit empty scope dimensions count false positives and retain perfect empty recall`() {
        assertEquals(
            LocalizationDimensionScores(0.0, 1.0, 0.0),
            LocalizationSetMetrics.score(setOf("extra"), emptySet()),
        )
        assertEquals(LocalizationDimensionScores(1.0, 1.0, 1.0), LocalizationSetMetrics.score(emptySet(), emptySet()))
        assertEquals(
            LocalizationDimensionScores(0.0, 0.0, 0.0),
            LocalizationSetMetrics.score(emptySet(), setOf("missing")),
        )
    }

    @Test
    fun `explicit empty scope gold is scored while absent gold remains unmeasured`() {
        val measured = LocalizationTask("task", "change", listOf("source"), changeScope = LocalizationGoldScope())
        val scores = LocalizationAnalysisMetrics.score(result(emptyList()), measured, structure)

        assertEquals(1.0, scores.modulePrecision)
        assertEquals(1.0, scores.moduleRecall)
        assertEquals(1.0, scores.moduleExactMatch)
        assertEquals(1.0, scores.sourceSetPrecision)
        assertEquals(1.0, scores.sourceSetRecall)
        assertEquals(1.0, scores.sourceSetExactMatch)
        assertEquals(1.0, scores.variantPrecision)
        assertEquals(1.0, scores.variantRecall)
        assertEquals(1.0, scores.variantExactMatch)
        assertEquals(1.0, scores.scopeAccuracy)
        val unmeasured = LocalizationAnalysisMetrics.score(
            result(emptyList()),
            measured.copy(changeScope = null),
            structure,
        )
        assertNull(unmeasured.scopeAccuracy)
    }

    private fun result(tests: List<RankedFile>) = AnalysisResult(
        query = AnalyzedQuery("change", emptyList(), emptyList(), emptyList(), emptyList()),
        files = emptyList(),
        symbols = emptyList(),
        modules = emptyList(),
        tests = tests,
        boundaries = emptyList(),
        scope = ChangeScope(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), false),
        risks = emptyList(),
        unresolved = emptyList(),
        evidence = emptyMap(),
        confidence = "none",
    )
}
