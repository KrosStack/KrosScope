package io.github.krosstack.kscope.cli.output

import io.github.krosstack.kscope.cli.text.ResultFixtures
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyzeJsonViewTest {
    private fun symbol(index: Int) = RankedSymbol(
        Symbol(
            SymbolId("s$index"), SymbolKind("kotlin.function", SymbolCategory.CALLABLE), "s$index", null, null, null,
            null, emptySet(), SymbolOrigin.Source, null, null, null, Scope(), EvidenceClass.COMPILER, Certainty.EXACT,
        ),
        1.0 / (index + 1),
    )

    private fun analysis(symbols: Int): AnalysisResult =
        ResultFixtures.analysis().copy(symbols = (1..symbols).map(::symbol))

    @Test
    fun `symbols are the top 50 by default, 10 when brief and 1000 when full`() {
        val result = analysis(symbols = 1_200)
        val expected = mapOf(DetailLevel.BRIEF to 10, DetailLevel.STANDARD to 50, DetailLevel.FULL to 1_000)

        expected.forEach { (detail, size) ->
            val view = AnalyzeJsonView.of(result, detail)

            assertEquals(result.symbols.take(size), view.symbols, detail.name)
            assertEquals(1_200 - size, view.symbolsOmitted, detail.name)
        }
    }

    @Test
    fun `a ranking shorter than the cap is listed whole with nothing omitted`() {
        val result = analysis(symbols = 7)

        DetailLevel.entries.forEach {
            val view = AnalyzeJsonView.of(result, it)

            assertEquals(result.symbols, view.symbols)
            assertEquals(0, view.symbolsOmitted)
        }
    }

    @Test
    fun `files follow the relevance cutoff unless the detail is full`() {
        val result = analysis(symbols = 0)

        val standard = AnalyzeJsonView.of(result, DetailLevel.STANDARD)
        val brief = AnalyzeJsonView.of(result, DetailLevel.BRIEF)
        val full = AnalyzeJsonView.of(result, DetailLevel.FULL)

        assertEquals(result.files.take(16), standard.files)
        assertEquals(20, standard.filesOmitted)
        assertEquals(standard.files, brief.files)
        assertEquals(result.files, full.files)
        assertEquals(0, full.filesOmitted)
    }

    @Test
    fun `files are not cut when learned ranking is applied`() {
        listOf(SemanticState.USED, SemanticState.BEHIND).forEach { state ->
            val result = analysis(symbols = 0).copy(semantic = SemanticStatus(state))

            val view = AnalyzeJsonView.of(result, DetailLevel.STANDARD)

            assertEquals(result.files, view.files, state.name)
            assertEquals(0, view.filesOmitted, state.name)
        }
    }

    @Test
    fun `the other lists of the analysis are not cut`() {
        val result = analysis(symbols = 100)

        val view = AnalyzeJsonView.of(result, DetailLevel.BRIEF)

        assertEquals(result.tests, view.tests)
        assertEquals(result.modules, view.modules)
        assertEquals(result.contextEstimates, view.contextEstimates)
    }
}
