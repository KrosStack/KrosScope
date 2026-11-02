package io.github.krosstack.kscope.benchmark.gold

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.LocalizationSymbolAddress
import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal class GoldSymbolMatcher(
    private val structure: WorkspaceStructure,
    private val names: WorkspaceNames = WorkspaceNames.SINGLE,
) {
    fun matches(symbol: Symbol, address: LocalizationSymbolAddress): Boolean {
        val sourceSet = structure.sourceSets.firstOrNull { it.id == symbol.scope.sourceSet }
        val module = structure.modules.firstOrNull { it.id == sourceSet?.module }?.path
            ?: moduleByLocation(symbol)
        return listOf(
            declarationMatches(symbol, address),
            address.sourceSet == null || address.sourceSet == sourceSet?.name,
            address.module == null || address.module == module,
            signatureMatches(symbol, address),
        ).all { it }
    }

    private fun moduleByLocation(symbol: Symbol): String? {
        val path = (symbol.nameLocation ?: symbol.declaration)?.artifact?.path?.value ?: return null
        return structure.modules.filter {
            it.directory.value.isEmpty() || path.startsWith("${it.directory.value}/")
        }.maxByOrNull { it.directory.value.length }?.path
    }

    private fun declarationMatches(symbol: Symbol, address: LocalizationSymbolAddress): Boolean {
        val location = symbol.nameLocation ?: symbol.declaration
        return listOf(
            location?.artifact?.let(names::of) == address.file,
            address.fqName == null || address.fqName == symbol.qualifiedName,
            address.name == null || address.name == symbol.name,
            GoldSymbolKinds.matches(symbol.kind.id, address.kind),
            address.line == null || address.line == location?.range?.startLine,
        ).all { it }
    }

    private fun signatureMatches(symbol: Symbol, address: LocalizationSymbolAddress): Boolean {
        val signature = address.signature
        if (signature == null || address.line != null) return true
        val identity = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity
        val method = identity?.descriptor?.segments?.lastOrNull() as? DescriptorSegment.Method
        val actual = method?.disambiguator?.split(',')?.filter { it.isNotBlank() }
            ?.map { it.substringAfter(':').lowercase() }
        val expected = signature.removeSurrounding("(", ")").split(',').filter { it.isNotBlank() }
            .map { it.trim().substringAfterLast('.').lowercase() }
        return actual == expected
    }
}
