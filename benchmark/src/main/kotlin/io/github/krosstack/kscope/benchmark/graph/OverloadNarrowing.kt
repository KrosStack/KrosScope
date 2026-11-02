package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol

/** Narrows the candidates that line and name leave ambiguous; a criterion that would exclude them all is ignored. */
internal class OverloadNarrowing(private val index: GraphIndex) {
    fun byModule(candidates: List<Symbol>, address: GoldAddress): List<Symbol> {
        val wanted = address.module ?: return candidates
        return candidates.filter { moduleOf(it) == wanted }.ifEmpty { candidates }
    }

    private fun moduleOf(symbol: Symbol): String? {
        val unit = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit
        return unit?.let { index.modulesById[it] }?.let(index::goldPathOf)
    }

    fun bySourceSet(candidates: List<Symbol>, address: GoldAddress): List<Symbol> {
        val name = address.sourceSet ?: return candidates
        return candidates.filter { it.scope.sourceSet?.value?.endsWith("@$name") == true }.ifEmpty { candidates }
    }

    fun bySignature(candidates: List<Symbol>, address: GoldAddress): List<Symbol> {
        val wanted = address.signature?.let(::goldParameterTypes) ?: return candidates
        return candidates.filter { parameterTypesOf(it) == wanted }.ifEmpty { candidates }
    }

    private fun goldParameterTypes(signature: String): List<String> =
        signature.removePrefix("(").removeSuffix(")").split(',').filter { it.isNotBlank() }
            .map { it.trim().substringAfterLast('.').lowercase() }

    private fun parameterTypesOf(symbol: Symbol): List<String> {
        val identity = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity
        val method = identity?.descriptor?.segments?.lastOrNull() as? DescriptorSegment.Method
        return method?.disambiguator.orEmpty().split(',').filter { it.isNotBlank() }
            .map { it.substringAfter(':').lowercase() }
    }
}
