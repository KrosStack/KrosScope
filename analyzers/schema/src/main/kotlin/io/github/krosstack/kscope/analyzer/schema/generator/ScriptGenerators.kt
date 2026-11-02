package io.github.krosstack.kscope.analyzer.schema.generator

import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.analyzer.schema.generator.script.InputKind
import io.github.krosstack.kscope.analyzer.schema.generator.script.ScriptInput
import io.github.krosstack.kscope.analyzer.schema.generator.specs.SpecTarget
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Module

private const val KOTLIN_GENERATOR = "kotlin"
private const val WIRE_KOTLIN_ATTRIBUTE = "wire.kotlin"

/**
 * What the inputs of the code generators of one build script make: the symbol of each input with its links to the
 * spec files, and the Kotlin classes that the generator makes of those files in the module of the script. Wire makes
 * classes in a module that has it generate Kotlin, from the proto files that are in no source set of the module. The
 * OpenAPI Generator makes them when its generator is `kotlin` and its packages are literals. Each file is made once
 * for a module, whatever number of inputs reach it, and a module with no source set to declare the classes in only
 * gets a note.
 */
internal class ScriptGenerators(
    private val script: Artifact,
    private val module: Module,
    private val facts: InputFacts,
    private val services: GeneratorServices,
) {
    fun outcome(inputs: List<ScriptInput>): GeneratorOutcome =
        inputs.map(::outcomeOf).fold(GeneratorOutcome.EMPTY, GeneratorOutcome::plus)

    private fun outcomeOf(input: ScriptInput): GeneratorOutcome {
        val targets = services.resolver.targetsOf(input, script, module)
        val stubs = when (input.kind) {
            InputKind.OPENAPI_SPEC -> clientStubs(input, targets)
            InputKind.WIRE_SOURCE_PATH -> wireStubs(targets)
        }
        return GeneratorOutcome(facts.of(input, targets), emptyList()) + stubs
    }

    private fun clientStubs(input: ScriptInput, targets: List<SpecTarget>): GeneratorOutcome {
        val settings = input.openApi
        val packages = settings?.packages()
        val home = StubHome.of(module, services.request.model)
        return when {
            targets.isEmpty() -> GeneratorOutcome.EMPTY

            settings?.generatorName != KOTLIN_GENERATOR -> note(
                "openapi-generator.generator-not-supported",
                "its OpenAPI generator is not kotlin, so the classes it makes are not synthesized",
            )

            packages == null -> note(
                "openapi-generator.package-unknown",
                "the packages of its OpenAPI generator are not literals, so the classes it makes are not synthesized",
            )

            home == null -> note(
                "openapi-generator.no-source-set",
                "its module has no main or commonMain source set to declare the classes of its OpenAPI generator in",
            )

            else -> targets.map { once(input.kind, it) { services.client.of(home, it, packages) } }
                .fold(GeneratorOutcome.EMPTY, GeneratorOutcome::plus)
        }
    }

    private fun wireStubs(targets: List<SpecTarget>): GeneratorOutcome {
        val pending = targets.filterNot(::isInModule)
        val home = StubHome.of(module, services.request.model)
        return when {
            module.attributes[WIRE_KOTLIN_ATTRIBUTE] != "true" || pending.isEmpty() -> GeneratorOutcome.EMPTY

            home == null -> note(
                "wire.no-source-set",
                "its module has no main or commonMain source set to declare the Kotlin classes of the proto files " +
                    "it reads in",
            )

            else -> pending.map { once(InputKind.WIRE_SOURCE_PATH, it) { services.wire.of(home, it) } }
                .fold(GeneratorOutcome.EMPTY, GeneratorOutcome::plus)
        }
    }

    private fun once(kind: InputKind, target: SpecTarget, make: () -> GeneratorOutcome): GeneratorOutcome {
        val ref = target.artifact.ref
        val key = "${module.id.value} $kind ${ref.repository.value} ${ref.path.value}"
        return if (services.firstTime(key)) make() else GeneratorOutcome.EMPTY
    }

    private fun isInModule(target: SpecTarget): Boolean =
        services.request.model.sourceSets.firstOrNull { it.id == target.artifact.sourceSet }?.module == module.id

    private fun note(code: String, message: String) =
        GeneratorOutcome(listOf(fileNote(script, DiagnosticSeverity.INFO, code, message)), emptyList())
}
