package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
public data class QueryExpansion(public val term: String, public val replacement: String, public val reason: String)

@Serializable
public data class AnalyzedQuery(
    public val text: String,
    public val terms: List<String>,
    public val exactMentions: List<String>,
    public val expansions: List<QueryExpansion>,
    public val hints: List<SymbolCategory>,
)

@Serializable
public data class SignalContribution(public val signal: SignalKind, public val score: Double)

@Serializable
public data class RankedFile(
    public val artifact: ArtifactRef,
    public val score: Double,
    public val role: FileRole,
    public val reasons: List<SignalContribution>,
)

@Serializable
public data class RankedSymbol(
    public val symbol: Symbol,
    public val score: Double,
    public val reasons: List<SignalContribution> = emptyList(),
)

@Serializable
public data class RankedModule(public val id: ModuleId, public val path: String, public val score: Double)

@Serializable
public data class ChangeScope(
    public val modules: List<ModuleId>,
    public val sourceSets: List<SourceSetId>,
    public val platforms: List<String>,
    public val variants: List<String>,
    public val publicSymbols: List<NodeRef>,
    public val crossModule: Boolean,
)

@Serializable
public data class RiskFact(public val node: NodeRef, public val name: String, public val value: Double)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
public data class AnalysisResult(
    public val query: AnalyzedQuery,
    public val files: List<RankedFile>,
    public val symbols: List<RankedSymbol>,
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
)

@Serializable
public data class ImpactEntry(
    public val node: NodeRef,
    public val distance: Int,
    public val certainty: String,
    public val path: List<Relationship>,
)

@Serializable
public data class ImpactResult(
    public val requested: String,
    public val roots: List<NodeRef>,
    public val affected: List<ImpactEntry>,
    public val scope: ChangeScope,
    public val tests: List<ArtifactRef>,
    public val unresolved: List<Relationship>,
)

@Serializable
public data class SymbolResult(public val query: AnalyzedQuery, public val symbols: List<RankedSymbol>)
