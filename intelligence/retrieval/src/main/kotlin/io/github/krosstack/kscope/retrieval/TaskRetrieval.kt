package io.github.krosstack.kscope.retrieval

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.retrieval.analysis.AnalysisFacts
import io.github.krosstack.kscope.retrieval.analysis.BoundaryPaths
import io.github.krosstack.kscope.retrieval.analysis.ImpactAnalysis
import io.github.krosstack.kscope.retrieval.analysis.QueryAnalyzer
import io.github.krosstack.kscope.retrieval.analysis.TestSelection
import io.github.krosstack.kscope.retrieval.ranking.FileRanking
import io.github.krosstack.kscope.retrieval.ranking.GraphRanking
import io.github.krosstack.kscope.retrieval.ranking.LexicalSeeds
import io.github.krosstack.kscope.retrieval.ranking.SymbolRanking
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.search.SearchHit
import io.github.krosstack.kscope.spi.search.SearchIndex
import io.github.krosstack.kscope.spi.search.SearchQuery
import io.github.krosstack.kscope.spi.search.SearchScope

public class TaskRetrieval(graph: GraphReader, private val search: SearchIndex) {
    private val graph = RetrievalGraph(graph)
    private val queries = QueryAnalyzer(search)
    private val facts = AnalysisFacts(this.graph)

    public fun analyze(
        text: String,
        stage: RetrievalStage = RetrievalStage.FULL,
        ml: MlMode = MlMode.ON,
    ): AnalysisResult {
        val query = queries.analyze(text)
        val symbols = search(query, SearchScope.SYMBOLS, SYMBOL_LIMIT)
        val artifacts = search(query, SearchScope.ARTIFACTS, ARTIFACT_LIMIT)
        val propagated = if (stage == RetrievalStage.LEXICAL) {
            emptyMap()
        } else {
            GraphRanking(graph).rank(LexicalSeeds.weights(symbols, artifacts))
        }
        val files = FileRanking(graph).rank(query, symbols, artifacts, stage, propagated)
        val ranked = SymbolRanking(graph).rank(symbols, propagated, stage)
        val edits = files.filter { it.role == FileRole.EDIT }
        val tests = TestSelection(graph).select(edits)
        val semantic = if (stage == RetrievalStage.LEXICAL || ml == MlMode.OFF) {
            SemanticStatus.OFF
        } else {
            SemanticStatus.NOT_INSTALLED
        }
        return result(query, files, ranked, tests).copy(semantic = semantic, stage = stage.name.lowercase())
    }

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

    public fun symbol(text: String): SymbolResult {
        val query = queries.analyze(text)
        val exact = graph.resolve(text).filterIsInstance<NodeRef.OfSymbol>().mapNotNull { graph.symbols[it.id] }
        val hits = search(query, SearchScope.SYMBOLS, SYMBOL_LIMIT)
        val propagated = GraphRanking(graph).rank(LexicalSeeds.weights(hits, emptyList()))
        val lexical = SymbolRanking(graph).rank(hits, propagated, RetrievalStage.FULL)
        val highest = lexical.maxOfOrNull { it.score } ?: 0.0
        return SymbolResult(query, (exact.map { RankedSymbol(it, highest + 1) } + lexical).distinctBy { it.symbol.id })
    }

    public fun impact(text: String): ImpactResult = ImpactAnalysis(graph).analyze(text)

    private fun search(query: AnalyzedQuery, scope: SearchScope, limit: Int): List<SearchHit> {
        val candidates = if (scope == SearchScope.ARTIFACTS) ARTIFACT_CANDIDATES else limit
        val matches = search.search(SearchQuery(QueryAnalyzer.searchTerms(query), scope, candidates))
        return matches.filter { hit -> usableSeed(hit.node) }.take(limit)
    }

    private fun usableSeed(node: NodeRef): Boolean {
        val artifact = (node as? NodeRef.OfArtifact)?.let { graph.artifacts[it.ref] }
        val generated = artifact?.generated
        return generated == null || generated.sourceOfTruth != null
    }

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
        const val ARTIFACT_CANDIDATES = 100
        const val DETAIL_LIMIT = 15
        const val CLEAR_MARGIN = 1.2
        val LEXICAL_SIGNALS = setOf(SignalKind.LEXICAL_SYMBOL, SignalKind.LEXICAL_ARTIFACT)
    }
}
