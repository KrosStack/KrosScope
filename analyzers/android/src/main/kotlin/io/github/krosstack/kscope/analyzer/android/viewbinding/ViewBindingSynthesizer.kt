package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.ResourceLocations
import io.github.krosstack.kscope.analyzer.android.note
import io.github.krosstack.kscope.analyzer.android.rclass.JavaNames
import io.github.krosstack.kscope.analyzer.android.rclass.StubHost
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import java.io.IOException

internal const val VIEW_BINDING_FEATURE = "android.buildFeatures.viewBinding"

private class ReadFile(val sourceSet: SourceSet, val name: String, val layout: ReadLayout)

/**
 * Makes the Java stub of the binding class of each layout of the modules that turn view binding on, from the naming
 * rules of the Android Gradle plugin, so that `ActivityMainBinding.inflate(...)` and `binding.title` bind. All the
 * configurations of a layout make one class. The unit is the module: the stubs depend on its layouts and its build
 * definition only.
 */
internal class ViewBindingSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.view-binding", "0.1.0"),
        consumes = setOf(AndroidArtifactTypes.layout),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.MODULE,
    )

    private val reader = LayoutViewReader()

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
        request.units.flatMap { unit ->
            val module = unit.module
            if (module == null || module.attributes[VIEW_BINDING_FEATURE] != "true") {
                emptyList()
            } else {
                val outcome = outcomeOf(module, unit, request)
                sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), outcome.facts))
                outcome.sources
            }
        }

    private class Outcome(val facts: List<Fact>, val sources: List<SyntheticSource>)

    private fun outcomeOf(module: Module, unit: AnalysisUnit, request: ProduceRequest): Outcome {
        val namespace = module.androidNamespace?.takeIf(JavaNames::isPackage)
        val host = StubHost.of(request.model.sourceSets.filter { it.module == module.id }, request.model.targets)
        if (namespace == null || host == null) return Outcome(listOf(unusable(module)), emptyList())
        val files = unit.artifacts.mapNotNull { readFile(it, request) }
        val bindable = files.mapNotNull { file ->
            (file.layout as? ReadLayout.Bindable)?.let { LayoutFile(file.sourceSet, file.name, it.views) }
        }
        val plan = BindingPlanner(module, namespace, host).plan(bindable)
        val skipped = files.map { it.name }.distinct().size - plan.classes.size
        val facts = plan.classes.flatMap { BindingFacts(plan, it).facts() } + notes(module, plan, skipped)
        return Outcome(facts, plan.classes.map { sourceOf(plan, it) })
    }

    private fun readFile(artifact: Artifact, request: ProduceRequest): ReadFile? {
        val sourceSet = artifact.sourceSet?.let { sourceSetOf(it, request) }?.takeIf { it.role == SourceSetRole.MAIN }
        val location = ResourceLocations.of(artifact.ref.path.value)?.takeIf { it.isXml }
        if (sourceSet == null || location == null) return null
        val layout = try {
            reader.read(request.files.read(artifact.ref))
        } catch (_: IOException) {
            ReadLayout.Empty
        }
        return ReadFile(sourceSet, location.baseName, layout)
    }

    private fun sourceSetOf(id: SourceSetId, request: ProduceRequest): SourceSet? =
        request.model.sourceSets.firstOrNull { it.id == id }

    private fun sourceOf(plan: BindingPlan, binding: BindingClass) = SyntheticSource(
        virtualPath = "android-viewbinding/${plan.packageName.replace('.', '/')}/${binding.name}.java",
        language = "java",
        text = BindingStubText.render(plan.packageName, binding),
        sourceSet = plan.host.id,
        generator = VIEW_BINDING_GENERATOR,
    )

    private fun unusable(module: Module) = module.note(
        DiagnosticSeverity.INFO,
        "android.viewbinding.namespace-unknown",
        "view binding is on but the module has no usable Android namespace or main source set; no binding classes " +
            "are made",
    )

    private fun notes(module: Module, plan: BindingPlan, skippedLayouts: Int): List<Fact> {
        val skippedFields = plan.classes.sumOf { it.skippedFields }
        return listOfNotNull(
            skippedLayouts.takeIf { it > 0 }?.let {
                module.note(
                    DiagnosticSeverity.INFO,
                    "android.viewbinding.layout-skipped",
                    "$it layouts have no binding class: they are ignored, are data binding layouts or have names " +
                        "that are no Java names",
                )
            },
            skippedFields.takeIf { it > 0 }?.let {
                module.note(
                    DiagnosticSeverity.INFO,
                    "android.viewbinding.field-skipped",
                    "$it ids have no field because their names are no Java names or repeat another",
                )
            },
        )
    }
}
