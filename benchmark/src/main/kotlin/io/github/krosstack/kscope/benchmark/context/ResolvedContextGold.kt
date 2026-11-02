package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.model.ArtifactRef

internal data class ResolvedGoldSymbol(val address: LocalizationSymbolAddress, val spans: List<SourceSpan>)

internal data class ResolvedContextGold(
    val files: Set<ArtifactRef>,
    val symbols: List<ResolvedGoldSymbol>,
    val excludedMissingFiles: List<String> = emptyList(),
) {
    val unmappedSymbols: List<LocalizationSymbolAddress>
        get() = symbols.filter { it.spans.isEmpty() }.map { it.address }
}
