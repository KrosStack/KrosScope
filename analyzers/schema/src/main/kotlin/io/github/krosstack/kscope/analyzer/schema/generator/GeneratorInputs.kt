package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.analyzer.schema.generator.script.BuildScriptReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.spi.ProduceRequest
import java.io.IOException

/**
 * Reads the build scripts of every repository of a workspace for the inputs of their code generators, and makes what
 * those inputs give the workspace: the symbols of the inputs, their links to the spec files they read, and the Kotlin
 * classes the generators make of them. A script that mentions no generator is not read into tokens. A script that
 * cannot be read, and a script with inputs that no module contains, get a note.
 */
internal class GeneratorInputs(private val request: ProduceRequest) {
    private val services = GeneratorServices(request)

    fun outcome(): GeneratorOutcome =
        services.scripts.map(::outcomeOf).fold(GeneratorOutcome.EMPTY, GeneratorOutcome::plus).normalized()

    private fun outcomeOf(script: Artifact): GeneratorOutcome {
        val text = textOf(script)
        val inputs = text?.takeIf(BuildScriptReader::mayHaveInputs)?.let { BuildScriptReader(it).inputs() }.orEmpty()
        val module = Placement.of(script, request.model)?.module
        return when {
            text == null -> note(script, "generator.unreadable", "it could not be read")

            inputs.isEmpty() -> GeneratorOutcome.EMPTY

            module == null -> note(script, "generator.module-unknown", "no module contains it")

            else -> {
                val facts = InputFacts(script, Placement(module, null), text)
                ScriptGenerators(script, module, facts, services).outcome(inputs)
            }
        }
    }

    private fun textOf(script: Artifact): String? = try {
        request.files.read(script.ref).toString(Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }

    private fun note(script: Artifact, code: String, message: String) =
        GeneratorOutcome(listOf(fileNote(script, DiagnosticSeverity.INFO, code, message)), emptyList())
}
