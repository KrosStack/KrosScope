package io.github.krosstack.kscope.retrieval.ranking

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.retrieval.AnalyzedQuery
import io.github.krosstack.kscope.retrieval.FileRole
import io.github.krosstack.kscope.retrieval.RankedFile
import io.github.krosstack.kscope.retrieval.RetrievalGraph
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.retrieval.SignalContribution
import io.github.krosstack.kscope.retrieval.SignalKind
import io.github.krosstack.kscope.spi.search.SearchHit

internal class FileRanking(private val graph: RetrievalGraph) {
    fun rank(
        query: AnalyzedQuery,
        symbols: List<SearchHit>,
        artifacts: List<SearchHit>,
        stage: RetrievalStage = RetrievalStage.FULL,
        propagated: Map<NodeRef, Double> = if (stage == RetrievalStage.LEXICAL) {
            emptyMap()
        } else {
            GraphRanking(graph).rank(LexicalSeeds.weights(symbols, artifacts))
        },
    ): List<RankedFile> {
        val lexicalSymbols = project(symbols.associate { it.node to it.score }, stage)
        val lexicalFiles = project(artifacts.associate { it.node to it.score }, stage)
        val expanded = if (stage == RetrievalStage.LEXICAL) emptyMap() else project(propagated, stage)
        val candidates = lexicalSymbols.keys + lexicalFiles.keys + expanded.keys
        val signals = signals(query, stage, lexicalSymbols, lexicalFiles, expanded)
        val ranked = (candidates + exact(query, stage).keys).map { file ->
            val reasons = signals.mapNotNull { it[file] }
            RankedFile(file, reasons.sumOf { it.score }, FileRole.READ, reasons)
        }.filter { it.score > 0 && graph.artifacts[it.artifact]?.generated == null }
            .sortedWith(compareByDescending<RankedFile> { it.score }.thenBy { graph.locations.pathOf(it.artifact) })
        val cut = editCut(ranked)
        val edits = ranked.take(cut).map { it.artifact }.toSet()
        return ranked.map { if (it.artifact in edits) it.copy(role = FileRole.EDIT) else it }
    }

    private fun signals(
        query: AnalyzedQuery,
        stage: RetrievalStage,
        lexicalSymbols: Map<ArtifactRef, Double>,
        lexicalFiles: Map<ArtifactRef, Double>,
        expanded: Map<ArtifactRef, Double>,
    ): List<Map<ArtifactRef, SignalContribution>> {
        val candidates = lexicalSymbols.keys + lexicalFiles.keys + expanded.keys
        return listOf(
            signal(SignalKind.LEXICAL_SYMBOL, lexicalSymbols, RankingWeights.SYMBOL),
            signal(SignalKind.LEXICAL_ARTIFACT, lexicalFiles, RankingWeights.ARTIFACT),
            signal(SignalKind.GRAPH, expanded, RankingWeights.GRAPH),
            signal(
                SignalKind.CATEGORY,
                if (stage >= RetrievalStage.CATEGORY) category(query, candidates) else emptyMap(),
                RankingWeights.CATEGORY,
            ),
            signal(
                SignalKind.HISTORY,
                if (stage == RetrievalStage.FULL) history(candidates) else emptyMap(),
                RankingWeights.HISTORY,
            ),
            signal(SignalKind.EXACT, exact(query, stage), RankingWeights.EXACT),
        )
    }

    private fun project(scores: Map<NodeRef, Double>, stage: RetrievalStage): Map<ArtifactRef, Double> = scores.entries
        .mapNotNull { (node, score) -> file(node, stage)?.let { it to score } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.max() + RankingWeights.DAMPING * (values.sum() - values.max()) }

    private fun file(node: NodeRef, stage: RetrievalStage): ArtifactRef? =
        if (stage >= RetrievalStage.PROJECTION) graph.file(node) else graph.declaredFile(node)

    private fun signal(name: SignalKind, scores: Map<ArtifactRef, Double>, weight: Double) = scores.entries
        .filter { it.value > 0 }.sortedWith(
            compareByDescending<Map.Entry<ArtifactRef, Double>> { it.value }
                .thenBy { graph.locations.pathOf(it.key) },
        )
        .mapIndexed { index, entry ->
            entry.key to SignalContribution(name, weight / (RankingWeights.FUSION_OFFSET + index + 1))
        }
        .toMap()

    private fun category(query: AnalyzedQuery, candidates: Set<ArtifactRef>): Map<ArtifactRef, Double> =
        graph.symbols.values.filter { it.kind.category in query.hints }
            .mapNotNull { graph.file(NodeRef.OfSymbol(it.id)) }.filter { it in candidates }
            .distinct().associateWith { 1.0 }

    private fun exact(query: AnalyzedQuery, stage: RetrievalStage): Map<ArtifactRef, Double> = query.exactMentions
        .flatMap(graph::resolve).mapNotNull { file(it, stage) }.distinct().associateWith { 1.0 }

    private fun history(candidates: Set<ArtifactRef>): Map<ArtifactRef, Double> = candidates.associateWith { file ->
        graph.reader.metrics(NodeRef.OfArtifact(file)).filter { it.name == "history.commits" }.sumOf { it.value }
    }

    private fun editCut(files: List<RankedFile>): Int {
        if (files.isEmpty()) return 0
        return files.take(MAX_EDITS + 1).zipWithNext().mapIndexed { index, (current, next) ->
            index + 1 to (current.score - next.score) / current.score
        }.maxByOrNull { it.second }?.first ?: 1
    }

    companion object {
        private const val MAX_EDITS = 15
    }
}
