package io.github.krosstack.kscope.cli.output

import io.github.krosstack.kscope.cli.text.SemanticAnalyzeText
import io.github.krosstack.kscope.cli.text.format.DetailLevel
import io.github.krosstack.kscope.cli.text.format.RelevanceCutoff
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.service.analysis.AnalysisJson

/**
 * The JSON of `analyze` lists the top symbols and the relevant files only; the full ranking stays in memory for the
 * readers that need it. Symbols are cut at [BRIEF_SYMBOLS], [STANDARD_SYMBOLS] or [FULL_SYMBOLS] by detail. Files
 * follow the text output: the [RelevanceCutoff], none when semantic ranking was applied, all of them at full detail.
 */
internal object AnalyzeJsonView {
    const val BRIEF_SYMBOLS = 10
    const val STANDARD_SYMBOLS = 50
    const val FULL_SYMBOLS = 1_000

    fun of(result: AnalysisResult, detail: DetailLevel): AnalysisJson {
        val symbols = when (detail) {
            DetailLevel.BRIEF -> BRIEF_SYMBOLS
            DetailLevel.STANDARD -> STANDARD_SYMBOLS
            DetailLevel.FULL -> FULL_SYMBOLS
        }
        return AnalysisJson.of(result, files(result, detail), result.symbols.take(symbols))
    }

    private fun files(result: AnalysisResult, detail: DetailLevel): List<RankedFile> =
        if (detail == DetailLevel.FULL || SemanticAnalyzeText.isApplied(result.semantic)) {
            result.files
        } else {
            RelevanceCutoff.select(result.files) { it.score }.relevant
        }
}
