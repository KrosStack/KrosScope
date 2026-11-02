package io.github.krosstack.kscope.analyzer.android.buildconfig

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.note
import io.github.krosstack.kscope.analyzer.android.rclass.JavaNames
import io.github.krosstack.kscope.analyzer.android.rclass.StubHost
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.ModuleTrait
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource

internal const val BUILD_CONFIG_FEATURE = "android.buildFeatures.buildConfig"

private val buildScriptType = AndroidArtifactTypes.buildScript
private val scriptPreference = listOf("build.gradle.kts", "build.gradle")

/**
 * Makes the Java stub of `BuildConfig` for each Android module that turns the feature on: the constants the plugin
 * always makes, those that depend on flavors and on the kind of module, and the `buildConfigField`s of the build. The
 * build script is the source of truth. The unit is the module: the stub depends on its build definition only.
 */
internal class BuildConfigSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.build-config", "0.1.0"),
        consumes = setOf(buildScriptType),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.MODULE,
    )

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
        request.units.flatMap { unit ->
            val module = unit.module
            if (module == null || !hasBuildConfig(module)) {
                emptyList()
            } else {
                val outcome = outcomeOf(module, unit, request)
                sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(""), outcome.facts))
                listOfNotNull(outcome.source)
            }
        }

    private class Outcome(val facts: List<Fact>, val source: SyntheticSource?)

    private fun hasBuildConfig(module: Module): Boolean {
        val isAndroid = module.traits.any { it == ModuleTrait.ANDROID_APPLICATION || it == ModuleTrait.ANDROID_LIBRARY }
        return module.attributes[BUILD_CONFIG_FEATURE] == "true" && isAndroid
    }

    private fun outcomeOf(module: Module, unit: AnalysisUnit, request: ProduceRequest): Outcome {
        val namespace = module.androidNamespace?.takeIf(JavaNames::isPackage)
        val host = StubHost.of(request.model.sourceSets.filter { it.module == module.id }, request.model.targets)
        val script = scriptOf(module, unit)
        return when {
            namespace == null -> skipped(module, "namespace-unknown", "the module has no usable Android namespace")

            host == null -> skipped(module, "no-source-set", "the module has no main source set to declare it in")

            script == null -> skipped(module, "no-build-script", "the module has no build script that declares it")

            else -> {
                val selection = selectFields(module, request)
                made(BuildConfigPlan(module, namespace, host, script, selection.fields), selection.skipped)
            }
        }
    }

    private fun scriptOf(module: Module, unit: AnalysisUnit): Artifact? = unit.artifacts
        .filter { it.ref.path.value.substringBeforeLast('/', "") == module.directory.value }
        .minByOrNull { scriptPreference.indexOf(it.ref.path.value.substringAfterLast('/')) }

    private class Selection(val fields: List<ConfigField>, val skipped: Int)

    private fun selectFields(module: Module, request: ProduceRequest): Selection {
        val hasFlavors = request.model.variants.any { it.module == module.id && it.flavors.isNotEmpty() }
        val dimensions = module.attributes[FLAVOR_DIMENSIONS_ATTRIBUTE]?.split(',').orEmpty()
        val standard = ConfigFields.standard(ModuleTrait.ANDROID_APPLICATION in module.traits, hasFlavors, dimensions)
        val taken = standard.map { it.name }.toSet()
        val declared = ConfigFields.declared(module.attributes).filter { it.name !in taken }
        val usable = declared.filter(ConfigFields::isUsable)
        return Selection(standard + usable, declared.size - usable.size)
    }

    private fun made(plan: BuildConfigPlan, skippedFields: Int): Outcome {
        val source = SyntheticSource(
            virtualPath = "android-buildconfig/${plan.namespace.replace('.', '/')}/${BuildConfigPlan.CLASS_NAME}.java",
            language = "java",
            text = BuildConfigStubText.render(plan),
            sourceSet = plan.host.id,
            generator = BUILD_CONFIG_GENERATOR,
        )
        val skipped = skippedFields.takeIf { it > 0 }?.let {
            plan.module.note(
                DiagnosticSeverity.INFO,
                "android.buildconfig.field-skipped",
                "$it build config fields have names or types that Java cannot take and are left out",
            )
        }
        return Outcome(BuildConfigFacts(plan).facts() + listOfNotNull(skipped), source)
    }

    private fun skipped(module: Module, code: String, reason: String) = Outcome(
        listOf(module.note(DiagnosticSeverity.INFO, "android.buildconfig.$code", "$reason; no BuildConfig is made")),
        null,
    )
}
