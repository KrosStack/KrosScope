package io.github.krosstack.kscope.analyzer.compose.res

import io.github.krosstack.kscope.analyzer.compose.ComposeArtifactTypes
import io.github.krosstack.kscope.analyzer.compose.ComposeResourceLocations
import io.github.krosstack.kscope.analyzer.compose.ComposeResourceReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import java.io.IOException

private class Outcome(val facts: List<Fact>, val source: SyntheticSource?)

/**
 * Makes the Kotlin stub of the resource class `Res` for each module that applies the Compose Multiplatform plugin and
 * has resources, from the naming rules of the plugin, so that `Res.string.app_name` binds. The package is the one the
 * build sets or, failing that, the one the plugin derives, which is only a heuristic. The unit is the module: the stub
 * depends on its resource files and its build definition only.
 */
internal class ComposeResSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.compose.res", "0.1.0"),
        consumes = ComposeArtifactTypes.all,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.MODULE,
    )

    private val reader = ComposeResourceReader()

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
        request.units.flatMap { unit ->
            val module = unit.module
            if (module == null || COMPOSE_PLUGIN_ID !in module.plugins) {
                emptyList()
            } else {
                val outcome = outcomeOf(module, unit, request)
                sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), outcome.facts))
                listOfNotNull(outcome.source)
            }
        }

    private fun outcomeOf(module: Module, unit: AnalysisUnit, request: ProduceRequest): Outcome {
        val pkg = ResPackages.of(module)
        val host = ResHost.of(request.model.sourceSets.filter { it.module == module.id })
        return when {
            pkg == null -> skipped(module, "package-unusable", "its resource package is no Kotlin package")

            host == null -> skipped(module, "no-source-set", "it has no main source set to declare the class in")

            else -> {
                val definitions = DefinitionReader(reader, request).definitionsOf(module, unit)
                made(ResPlanner.plan(module, pkg, host, definitions))
            }
        }
    }

    private fun made(plan: ResPlan): Outcome {
        val source = SyntheticSource(
            virtualPath = "compose-res/${plan.pkg.name.replace('.', '/')}/Res.kt",
            language = "kotlin",
            text = ResStubText.render(plan),
            sourceSet = plan.host.id,
            generator = RES_GENERATOR,
        )
        val skipped = plan.skippedNames.takeIf { it > 0 }
            ?.let { note(plan.module, "name-skipped", "$it resource names are no Kotlin names and have no accessor") }
        return Outcome(ResFacts(plan).facts() + listOfNotNull(skipped), source)
    }

    private fun skipped(module: Module, code: String, reason: String) =
        Outcome(listOf(note(module, code, "$reason; no Res class is made")), null)

    private fun note(module: Module, code: String, message: String) = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.INFO, ReasonCode("compose.res.$code"), "${module.path}: $message.", null),
    )
}

/** The resources that the main source sets of a module define, read from the resource files of its unit. */
private class DefinitionReader(private val reader: ComposeResourceReader, private val request: ProduceRequest) {
    fun definitionsOf(module: Module, unit: AnalysisUnit): List<OwnedDefinition> =
        unit.artifacts.filter { it.type in ComposeArtifactTypes.named }
            .sortedBy { it.ref.path.value }
            .flatMap { definitionsIn(module, it) }

    private fun definitionsIn(module: Module, artifact: Artifact): List<OwnedDefinition> {
        val sourceSet = request.model.sourceSets.firstOrNull { it.id == artifact.sourceSet }
        val location = ComposeResourceLocations.of(artifact.ref.path.value)
        if (sourceSet == null || location == null || sourceSet.role != SourceSetRole.MAIN) return emptyList()
        return try {
            reader.read(location, request.files.read(artifact.ref), artifact.lineCount).definitions
                .map { OwnedDefinition(module, sourceSet, it) }
        } catch (_: IOException) {
            emptyList()
        }
    }
}
