package io.github.krosstack.kscope.analyzer.android.manifest

import io.github.krosstack.kscope.analyzer.android.AndroidArtifactTypes
import io.github.krosstack.kscope.analyzer.android.note
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

/**
 * Turns each manifest into the symbols of its application, components and permissions and the symbolic references from
 * them to implementing classes and to resources. The class linker and the resource linker answer the references.
 */
internal class ManifestProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.manifest", "0.1.0"),
        consumes = setOf(AndroidArtifactTypes.manifest),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val reader = ManifestReader()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(unit, artifact, request.files)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): List<Fact> {
        val module = unit.module
        val sourceSet = unit.sourceSet
        if (module == null || sourceSet == null) {
            val message = "no module owns it"
            return listOf(artifact.note(DiagnosticSeverity.INFO, "android.manifest.module-unknown", message))
        }
        return try {
            ManifestFacts(artifact, module, sourceSet, reader.read(files.read(artifact.ref))).facts()
        } catch (failure: IOException) {
            val message = "it could not be read: ${failure.message}"
            listOf(artifact.note(DiagnosticSeverity.WARNING, "android.manifest.unreadable", message))
        }
    }
}
