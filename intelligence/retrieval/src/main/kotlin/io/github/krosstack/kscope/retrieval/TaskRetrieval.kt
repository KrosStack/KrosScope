package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.retrieval.analysis.AnalysisFacts
import io.github.krosstack.kscope.retrieval.analysis.BoundaryPaths
import io.github.krosstack.kscope.retrieval.analysis.ImpactAnalysis
import io.github.krosstack.kscope.retrieval.analysis.QueryAnalyzer
import io.github.krosstack.kscope.retrieval.analysis.TestSelection
import io.github.krosstack.kscope.retrieval.ranking.FileRankFusion
import io.github.krosstack.kscope.retrieval.ranking.FileRanking
import io.github.krosstack.kscope.retrieval.ranking.FusedAnalysisFiles
import io.github.krosstack.kscope.retrieval.ranking.GraphRanking
import io.github.krosstack.kscope.retrieval.ranking.LexicalSeeds
import io.github.krosstack.kscope.retrieval.ranking.SymbolRanking
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchScope

public class TaskRetrieval internal constructor(graph: GraphReader, search: SearchIndex, mostCandidates: Int) {
    public constructor(graph: GraphReader, search: SearchIndex) : this(graph, search, SeedSearch.MOST_CANDIDATES)

    private val graph = RetrievalGraph(graph)
    private val queries = QueryAnalyzer(search)
    private val facts = AnalysisFacts(this.graph)
    private val seeds = SeedSearch(this.graph, search, mostCandidates)

    public fun analyze(
        text: String,
        stage: RetrievalStage = RetrievalStage.FULL,
        ml: MlMode = MlMode.ON,
        within: RepositoryId? = null,
    ): AnalysisResult {
        val scope = RepositoryScope(graph, within)
        val query = queries.analyze(text)
        val symbols = seeds.find(query, SearchScope.SYMBOLS, SYMBOL_LIMIT, scope)
        val artifacts = seeds.find(query, SearchScope.ARTIFACTS, ARTIFACT_LIMIT, scope)
        val propagated = if (stage == RetrievalStage.LEXICAL) {
            emptyMap()
        } else {
            scope.scores(GraphRanking(graph).rank(LexicalSeeds.weights(symbols.hits, artifacts.hits)))
        }
        val files = FileRanking(graph, within).rank(query, symbols.hits, artifacts.hits, stage, propagated)
        val ranked = scope.symbols(SymbolRanking(graph).rank(symbols.hits, propagated, stage))
        val edits = files.filter { it.role == FileRole.EDIT }
        val tests = TestSelection(graph).select(edits)
        val semantic = if (stage == RetrievalStage.LEXICAL || ml == MlMode.OFF) {
            SemanticStatus.OFF
        } else {
            SemanticStatus.NOT_INSTALLED
        }
        return result(query, files, ranked, tests).copy(
            semantic = semantic,
            stage = stage.name.lowercase(),
            searchCapped = symbols.capped || artifacts.capped,
        )
    }

    public fun withFileRanking(result: AnalysisResult, fused: List<FileRankFusion.Match>): AnalysisResult {
        val files = FusedAnalysisFiles.build(result.files, fused)
        val selected = files.map { it.artifact }.toSet()
        val symbols = result.symbols.filter { canonicalFile(it.symbol.id) in selected }
        val tests = TestSelection(graph).select(files.filter { it.role == FileRole.EDIT })
        return result(result.query, files, symbols, tests).copy(
            semantic = result.semantic,
            stage = result.stage,
            searchCapped = result.searchCapped,
        )
    }

    public fun canonicalFile(symbol: SymbolId): ArtifactRef? = graph.file(NodeRef.OfSymbol(symbol))

    private fun result(
        query: AnalyzedQuery,
        files: List<RankedFile>,
        symbols: List<RankedSymbol>,
        tests: List<RankedFile>,
    ): AnalysisResult {
        val edits = files.filter { it.role == FileRole.EDIT }
        val selected = files.take(DETAIL_LIMIT).map { it.artifact }.toSet()
        val near = facts.near(selected)
        val boundaryPaths = BoundaryPaths(graph).between(selected)
        return AnalysisResult(
            query, files, symbols, facts.modules(files), tests,
            near.filter { it.kind in AnalysisFacts.BOUNDARIES && it.target() != null },
            facts.scope((edits + tests).map { it.artifact }.toSet()), facts.risks(selected),
            near.filter { it.target() == null },
            near.groupingBy(::evidenceKey).eachCount().toSortedMap(),
            confidence(files),
            boundaryPaths.paths,
            boundaryPaths.truncated,
        )
    }

    public fun symbol(text: String, within: RepositoryId? = null): SymbolResult {
        val scope = RepositoryScope(graph, within)
        val query = queries.analyze(text)
        val exact = graph.resolve(text).filterIsInstance<NodeRef.OfSymbol>().mapNotNull { graph.symbols[it.id] }
        val hits = seeds.find(query, SearchScope.SYMBOLS, SYMBOL_LIMIT, scope)
        val propagated = scope.scores(GraphRanking(graph).rank(LexicalSeeds.weights(hits.hits, emptyList())))
        val lexical = scope.symbols(SymbolRanking(graph).rank(hits.hits, propagated, RetrievalStage.FULL))
        val highest = lexical.maxOfOrNull { it.score } ?: 0.0
        val symbols = (exact.map { RankedSymbol(it, highest + 1) } + lexical).distinctBy { it.symbol.id }
        return SymbolResult(query, symbols, hits.capped)
    }

    public fun impact(text: String): ImpactResult = ImpactAnalysis(graph).analyze(text)

    private fun confidence(files: List<RankedFile>): String {
        val first = files.firstOrNull() ?: return "none"
        val agreement = first.reasons.count { it.signal in LEXICAL_SIGNALS }
        val second = files.getOrNull(1)?.score ?: 0.0
        return if (agreement == 2 && first.score > second * CLEAR_MARGIN) "high" else "limited"
    }

    private fun evidenceKey(edge: Relationship): String {
        val state = when (val resolution = edge.resolution) {
            is Resolution.Resolved -> resolution.certainty.name.lowercase()
            is Resolution.Unresolved -> "unresolved"
            is Resolution.NotAnalysed -> "not-analysed"
        }
        return "${edge.evidenceClass.name.lowercase()}:$state"
    }

    private companion object {
        const val SYMBOL_LIMIT = 50
        const val ARTIFACT_LIMIT = 30
        const val DETAIL_LIMIT = 15
        const val CLEAR_MARGIN = 1.2
        val LEXICAL_SIGNALS = setOf(SignalKind.LEXICAL_SYMBOL, SignalKind.LEXICAL_ARTIFACT)
    }
}
