package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.stub.StubFile
import io.github.krosstack.kscope.analyzer.schema.stub.StubSymbols
import io.github.krosstack.kscope.analyzer.schema.stub.StubText
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.SyntheticSource

private val homeNames = listOf("main", "commonMain")

/**
 * The source set of a module that the Kotlin classes of a generator are declared in: its `main`, or else its
 * `commonMain`. The identities of their symbols name this module and this source set, whatever module the spec they
 * come from is in.
 */
internal class StubHome(val module: Module, val sourceSet: SourceSet) {
    private val placement = Placement(module, sourceSet)

    /** The synthetic source of [stub] at [virtualPath], with the facts about the symbols it declares. */
    fun stubbed(stub: StubFile, generator: String, virtualPath: String): GeneratorOutcome {
        val generated = StubSymbols(placement.identity, generator, placement.scope).of(stub)
        val source = SyntheticSource(virtualPath, "kotlin", StubText.render(stub), sourceSet.id, generator)
        val symbols = generated.symbols.map { Fact.OfSymbol(it) }
        return GeneratorOutcome(symbols + generated.relationships.map { Fact.OfRelationship(it) }, listOf(source))
    }

    companion object {
        /** Null when the module has neither source set. */
        fun of(module: Module, model: BuildModel): StubHome? {
            val owned = model.sourceSets.filter { it.module == module.id }
            val chosen = homeNames.firstNotNullOfOrNull { name -> owned.firstOrNull { it.name == name } }
            return chosen?.let { StubHome(module, it) }
        }
    }
}
