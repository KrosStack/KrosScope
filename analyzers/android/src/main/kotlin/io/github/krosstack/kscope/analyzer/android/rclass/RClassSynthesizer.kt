package io.github.krosstack.kscope.analyzer.android.rclass

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.modules.ModuleGraph
import io.github.krosstack.kscope.analyzer.android.resources.ResourceFileReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles

/**
 * Makes the Java stub of `R` for each Android module with a namespace, from the documented naming rules of the
 * Android Gradle plugin, so that references to `R.string.app_name` bind. The stub holds the module's own resources, or
 * those of its dependencies as well when the build asks for transitive `R` classes. The unit is the whole workspace,
 * because the stub of a module depends on the resources of other modules and on the Gradle properties.
 */
internal class RClassSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.r-class", "0.1.0"),
        consumes = AndroidArtifactTypes.resources + AndroidArtifactTypes.gradleProperties,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.WORKSPACE,
    )

    private val reader = ResourceFileReader()

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> {
        val unit = request.units.singleOrNull() ?: return emptyList()
        val builder = ModuleBuilder(request.model, request.files, reader, unit.artifacts)
        val outcomes = request.model.modules.sortedBy { it.id.value }.mapNotNull(builder::outcomeOf)
        val facts = outcomes.flatMap { it.facts }
        sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), facts))
        return outcomes.mapNotNull { it.source }
    }
}

private class Outcome(val facts: List<Fact>, val source: SyntheticSource?)

private class ModuleBuilder(
    private val model: BuildModel,
    files: WorkspaceFiles,
    reader: ResourceFileReader,
    artifacts: List<Artifact>,
) {
    private val resources: Map<ModuleId, List<OwnedDefinition>> =
        ModuleResources(reader, files).definitionsByModule(artifacts, model)
    private val options = RClassOptions(files, artifacts, model.buildRoots)
    private val graph = ModuleGraph(model.sourceSets, model.dependencies)

    fun outcomeOf(module: Module): Outcome? {
        val own = resources[module.id].orEmpty()
        if (own.isEmpty() && module.androidNamespace == null) return null
        val visible = own + dependencyResources(module)
        val namespace = module.androidNamespace?.takeIf(JavaNames::isPackage)
        val host = StubHost.of(model.sourceSets.filter { it.module == module.id }, model.targets)
        return when {
            visible.isEmpty() -> null
            namespace == null -> skipped(module, NAMESPACE_UNKNOWN, "it has resources but no usable Android namespace")
            host == null -> skipped(module, NO_SOURCE_SET, "it has no main source set to declare the class in")
            else -> made(RClassPlanner.plan(module, namespace, host, visible))
        }
    }

    private fun dependencyResources(module: Module): List<OwnedDefinition> =
        if (options.isNonTransitive(module.buildRoot)) {
            emptyList()
        } else {
            graph.closure(module.id).flatMap { resources[it].orEmpty() }
        }

    private fun made(plan: RClassPlan): Outcome {
        val source = SyntheticSource(
            virtualPath = "android-r/${plan.namespace.replace('.', '/')}/R.java",
            language = "java",
            text = JavaStubText.render(plan),
            sourceSet = plan.host.id,
            generator = R_GENERATOR,
        )
        val skipped = plan.skippedNames.takeIf { it > 0 }?.let {
            note(plan.module, NAME_SKIPPED, "$it resource names are not valid Java names and have no R field")
        }
        return Outcome(RClassFacts(plan).facts() + listOfNotNull(skipped), source)
    }

    private fun skipped(module: Module, code: String, reason: String) =
        Outcome(listOf(note(module, code, "$reason; no R class is made")), null)

    private fun note(module: Module, code: String, message: String) = Fact.OfDiagnostic(
        Diagnostic(DiagnosticSeverity.INFO, ReasonCode(code), "${module.path}: $message.", null),
    )

    private companion object {
        const val NAMESPACE_UNKNOWN = "android.r.namespace-unknown"
        const val NO_SOURCE_SET = "android.r.no-source-set"
        const val NAME_SKIPPED = "android.r.name-skipped"
    }
}
