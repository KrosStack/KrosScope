package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.spi.context.TokenEstimator

internal object ContextMetrics {
    fun score(
        context: ContextPackage,
        gold: ResolvedContextGold,
        documents: Map<ArtifactRef, SourceDocument>,
        estimator: TokenEstimator,
        names: WorkspaceNames = WorkspaceNames.SINGLE,
    ): ContextScores {
        val verified = VerifiedContextSource(documents, estimator).verify(context)
        val coverage = SourceCoverage(verified.spans)
        val files = gold.files.filter { coverage.byFile[it].orEmpty().isNotEmpty() }.sortedBy { it.path.value }
        val fullFiles = gold.files.filter { file ->
            documents[file]?.let { coverage.contains(SourceSpan(file, SourceInterval(0, it.text.length))) } == true
        }.sortedBy { it.path.value }
        val symbols = gold.symbols.filter { symbol -> symbol.spans.any(coverage::contains) }.map { it.address }
        val goldTokens = goldTokens(gold.files, coverage, documents, estimator)
        val totalTokens = estimator.count(context.markdown)
        return ContextScores(
            fraction(files.size + symbols.size, gold.files.size + gold.symbols.size),
            fraction(files.size, gold.files.size),
            if (gold.symbols.isEmpty()) null else fraction(symbols.size, gold.symbols.size),
            fraction(fullFiles.size, gold.files.size),
            fraction(goldTokens.value, totalTokens.value),
            totalTokens,
            goldTokens,
            files.map(names::of),
            fullFiles.map(names::of),
            symbols,
            verified.issues,
        )
    }

    private fun goldTokens(
        gold: Set<ArtifactRef>,
        coverage: SourceCoverage,
        documents: Map<ArtifactRef, SourceDocument>,
        estimator: TokenEstimator,
    ): TokenCount = TokenCount(
        gold.sortedBy { it.path.value }.sumOf { file ->
            val text = documents.getValue(file).text
            coverage.byFile[file].orEmpty().sumOf { estimator.count(text.substring(it.start, it.end)).value }
        },
    )

    private fun fraction(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator
}
