package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.aidl.AidlArtifactTypes
import io.github.krosstack.kscope.analyzer.android.aidl.AidlNotes
import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.analyzer.android.aidl.packageOf
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlFile
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParseResult
import io.github.krosstack.kscope.analyzer.android.aidl.parser.AidlParser
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.Module
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.SurfaceSynthesizer
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

private const val AIDL_FEATURE = "android.buildFeatures.aidl"

private class Outcome(val facts: List<Fact>, val sources: List<SyntheticSource>)

/**
 * Makes the Java surface of each `.aidl` file from the documented template of the AIDL generator: the interface, its
 * `Stub` with `asInterface` and `asBinder`, and its `Default` implementation, or the class of a structured parcelable.
 * References to `IFoo.Stub.asInterface` in Kotlin and Java then bind to these stubs. A module that turns the `aidl`
 * build feature off gets none. The stub of a file lies in the source set that holds the file.
 */
internal class AidlSurfaceSynthesizer : SurfaceSynthesizer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.aidl-surface", "0.1.0"),
        consumes = setOf(AidlArtifactTypes.source),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    override fun synthesize(request: ProduceRequest, sink: FactSink): List<SyntheticSource> =
        request.units.flatMap { unit ->
            val artifact = unit.artifacts.single()
            val outcome = outcomeOf(unit, artifact, request.files)
            val fingerprint = Fingerprint(artifact.contentHash.value)
            sink.accept(FactBatch(descriptor.producer, unit.owner, fingerprint, outcome.facts))
            outcome.sources
        }

    private fun outcomeOf(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): Outcome {
        val module = unit.module
        val sourceSet = unit.sourceSet
        val file = readFile(artifact, files)
        return when {
            module == null || sourceSet == null || file == null -> Outcome(emptyList(), emptyList())
            module.attributes[AIDL_FEATURE].equals("false", ignoreCase = true) -> disabled(artifact, module)
            else -> synthesized(module, sourceSet, file)
        }
    }

    private fun readFile(artifact: Artifact, files: WorkspaceFiles): AidlFile? {
        val text = try {
            files.read(artifact.ref).toString(Charsets.UTF_8)
        } catch (_: IOException) {
            return null
        }
        return (AidlParser.parse(text) as? AidlParseResult.Parsed)?.file
    }

    private fun disabled(artifact: Artifact, module: Module): Outcome {
        val message = "${module.path} turns the aidl build feature off, so no Java surface is made"
        val note = AidlNotes.of(artifact, DiagnosticSeverity.INFO, "surface-disabled", message)
        return Outcome(listOf(note), emptyList())
    }

    private fun synthesized(module: Module, sourceSet: SourceSet, file: AidlFile): Outcome {
        val planner = AidlStubPlanner(file)
        val facts = AidlStubFacts(module, sourceSet)
        val stubs = file.declarations.mapNotNull { declaration ->
            planner.plan(declaration)?.let { file.packageOf(declaration) to it }
        }
        return Outcome(
            facts = stubs.flatMap { (packageName, type) -> facts.facts(packageName, type) },
            sources = stubs.map { (packageName, type) -> sourceOf(packageName, type, sourceSet) },
        )
    }

    private fun sourceOf(packageName: String?, type: StubType, sourceSet: SourceSet) = SyntheticSource(
        virtualPath = (listOf("aidl") + AidlSymbols.packageSegments(packageName) + "${type.name}.java")
            .joinToString("/"),
        language = "java",
        text = AidlStubText.render(packageName, type),
        sourceSet = sourceSet.id,
        generator = AIDL_GENERATOR,
    )
}
