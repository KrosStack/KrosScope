package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.gold.GoldSymbolMatcher
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal class ContextGoldResolver(
    graph: GraphReader,
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val names: WorkspaceNames = WorkspaceNames.SINGLE,
) {
    private val matcher = GoldSymbolMatcher(graph.structure(), names)
    private val symbols = graph.symbols(SymbolQuery(limit = Int.MAX_VALUE))
    private val filesByPath = documents.keys.associateBy(names::of)

    fun resolve(gold: ContextGoldSpec): ResolvedContextGold {
        val paths = (gold.files + gold.symbols.map { it.file }).distinct().sorted()
        return ResolvedContextGold(
            paths.mapNotNull(filesByPath::get).toSet(),
            gold.symbols.distinct().map { ResolvedGoldSymbol(it, spans(it)) },
            paths.filter { it !in filesByPath },
        )
    }

    private fun spans(address: LocalizationSymbolAddress): List<SourceSpan> = symbols
        .filter { matcher.matches(it, address) }.mapNotNull { symbol ->
            symbol.declaration?.let { declaration ->
                documents[declaration.artifact]?.interval(declaration.range)?.takeIf { it.start < it.end }
                    ?.let { SourceSpan(declaration.artifact, it) }
            }
        }.distinct().sortedWith(compareBy<SourceSpan> { names.of(it.artifact) }.thenBy { it.interval.start })
}
