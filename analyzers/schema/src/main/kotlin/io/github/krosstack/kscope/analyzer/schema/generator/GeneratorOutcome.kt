package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.SyntheticSource

/** The facts and the synthetic sources that the generator inputs of a script, or of all scripts, add up to. */
internal class GeneratorOutcome(val facts: List<Fact>, val sources: List<SyntheticSource>) {
    operator fun plus(other: GeneratorOutcome) = GeneratorOutcome(facts + other.facts, sources + other.sources)

    /** Without repeated facts and with the sources in order; of two symbols with one id, the first is kept. */
    fun normalized(): GeneratorOutcome {
        val seen = HashSet<SymbolId>()
        val unique = facts.distinct().filter { it !is Fact.OfSymbol || seen.add(it.value.id) }
        return GeneratorOutcome(unique, sources.sortedWith(compareBy({ it.sourceSet.value }, { it.virtualPath })))
    }

    companion object {
        val EMPTY = GeneratorOutcome(emptyList(), emptyList())
    }
}
