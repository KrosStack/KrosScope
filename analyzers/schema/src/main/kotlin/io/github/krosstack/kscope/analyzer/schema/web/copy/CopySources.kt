package io.github.krosstack.kscope.analyzer.schema.web.copy

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.generator.specs.CheckoutRoots
import io.github.krosstack.kscope.analyzer.schema.generator.specs.ScriptLocations
import io.github.krosstack.kscope.analyzer.schema.web.facts.WebNotes
import io.github.krosstack.kscope.analyzer.schema.web.textOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.spi.ProduceRequest

/**
 * Reads the build scripts of every repository of a workspace for the paths that their tasks copy from, and makes the
 * symbols of those paths. A script that mentions no copy is not read into tokens. A script that cannot be read, and a
 * script with copies that no module contains, get a note. The checkouts that locate the paths are looked up when the
 * first script with a copy needs them, so that a workspace without copies pays nothing for them.
 */
internal class CopySources(private val request: ProduceRequest) {
    private val artifacts = (request.artifacts + request.units.flatMap { it.artifacts }).distinctBy { it.ref }
    private val locations by lazy { ScriptLocations(request.model, CheckoutRoots(request.files, artifacts)) }

    fun facts(): List<Fact> = artifacts.filter { it.type == SchemaArtifactTypes.buildScript }
        .sortedWith(compareBy({ it.ref.repository.value }, { it.ref.path.value }))
        .flatMap(::factsOf)

    private fun factsOf(script: Artifact): List<Fact> {
        val text = request.files.textOf(script)
        val sources = text?.takeIf(CopyScriptReader::mayHaveSources)?.let { CopyScriptReader(it).sources() }.orEmpty()
        val module = Placement.of(script, request.model)?.module
        return when {
            text == null -> listOf(WebNotes.unreadable(script))

            sources.isEmpty() -> emptyList()

            module == null -> listOf(WebNotes.moduleUnknown(script))

            else -> CopySourceFacts(script, Placement(module, null), text, locations).let { facts ->
                sources.map(facts::of)
            }
        }
    }
}
