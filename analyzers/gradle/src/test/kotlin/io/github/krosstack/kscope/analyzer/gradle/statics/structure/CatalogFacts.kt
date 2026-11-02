package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.BuildModelResult

/** The catalog symbols and the references to them in the result of the static provider. */
internal class CatalogFacts(provided: BuildModelResult.Provided) {
    val symbols: List<Symbol> = provided.facts.filterIsInstance<Fact.OfSymbol>().map { it.value }
    val references = provided.facts.filterIsInstance<Fact.OfRelationship>().map { it.value }

    fun symbol(kind: String, accessor: String): Symbol =
        symbols.single { it.kind.id == "gradle.catalog.$kind" && it.name == accessor }

    fun targetsOf(path: String): List<Pair<SymbolId, Int?>> = references
        .filter { (it.from as? NodeRef.OfArtifact)?.ref?.path?.value == path }
        .map { ((it.resolution as Resolution.Resolved).target as NodeRef.OfSymbol).id to it.site?.range?.startLine }
}
