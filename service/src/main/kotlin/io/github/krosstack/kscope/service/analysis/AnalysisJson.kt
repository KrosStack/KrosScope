package io.github.krosstack.kscope.service.analysis

import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.AnalysisResult
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.ChangeScope
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RankedModule
import io.github.krosstack.kscope.retrieval.RankedSymbol
import io.github.krosstack.kscope.retrieval.RiskFact
import io.github.krosstack.kscope.retrieval.SemanticStatus
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * What `analyze --format json` prints: an [AnalysisResult] whose [files] and [symbols] may be shorter than the
 * ranking. [filesOmitted] and [symbolsOmitted] count what was left out and are printed only above zero.
 */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class AnalysisJson(
    public val query: AnalyzedQuery,
    public val files: List<RankedFile>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val filesOmitted: Int = 0,
    public val symbols: List<RankedSymbol>,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val symbolsOmitted: Int = 0,
    public val modules: List<RankedModule>,
    public val tests: List<RankedFile>,
    public val boundaries: List<Relationship>,
    public val scope: ChangeScope,
    public val risks: List<RiskFact>,
    public val unresolved: List<Relationship>,
    public val evidence: Map<String, Int>,
    public val confidence: String,
    public val boundaryPaths: List<List<Relationship>> = emptyList(),
    public val boundaryPathsTruncated: Boolean = false,
    public val contextEstimates: Map<ContextLevel, TokenCount> = emptyMap(),
    public val semantic: SemanticStatus = SemanticStatus.NOT_INSTALLED,
    @EncodeDefault(EncodeDefault.Mode.NEVER) public val stage: String = "full",
) {
    public companion object {
        /** [result] with [files] and [symbols] as its lists; the counts of what is left out follow from them. */
        public fun of(result: AnalysisResult, files: List<RankedFile>, symbols: List<RankedSymbol>): AnalysisJson =
            AnalysisJson(
                query = result.query,
                files = files,
                filesOmitted = result.files.size - files.size,
                symbols = symbols,
                symbolsOmitted = result.symbols.size - symbols.size,
                modules = result.modules,
                tests = result.tests,
                boundaries = result.boundaries,
                scope = result.scope,
                risks = result.risks,
                unresolved = result.unresolved,
                evidence = result.evidence,
                confidence = result.confidence,
                boundaryPaths = result.boundaryPaths,
                boundaryPathsTruncated = result.boundaryPathsTruncated,
                contextEstimates = result.contextEstimates,
                semantic = result.semantic,
                stage = result.stage,
            )
    }
}
