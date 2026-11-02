package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.SyntheticSource

internal class BindingResult(val facts: List<Fact>, val sources: List<SyntheticSource>) {
    fun source(className: String): SyntheticSource = sources.single { it.virtualPath.endsWith("/$className.java") }

    fun symbols(): List<Symbol> = facts.filterIsInstance<Fact.OfSymbol>().map { it.value }

    fun generates(): List<Relationship> =
        facts.filterIsInstance<Fact.OfRelationship>().map { it.value }.filter { it.kind.name == "GENERATES" }
}
