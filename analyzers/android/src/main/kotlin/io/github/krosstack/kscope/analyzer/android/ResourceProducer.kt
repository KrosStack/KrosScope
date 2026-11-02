package io.github.krosstack.kscope.analyzer.android

import io.github.krosstack.kscope.analyzer.android.resources.ResourceFacts
import io.github.krosstack.kscope.analyzer.android.resources.ResourceFileReader
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

/**
 * Turns each Android resource file into the resource symbols it defines and the symbolic references it contains. The
 * references are answered by [ResourceLinker].
 */
internal class ResourceProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.android.resources", "0.2.0"),
        consumes = AndroidArtifactTypes.resources,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val reader = ResourceFileReader()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(unit, artifact, request.files)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): List<Fact> {
        val location = ResourceLocations.of(artifact.ref.path.value)
        val module = unit.module
        val sourceSet = unit.sourceSet
        return when {
            location == null -> emptyList()

            !location.isKnownType ->
                listOf(note(artifact, DiagnosticSeverity.INFO, "unknown-type", "its folder is not a resource type"))

            module == null || sourceSet == null ->
                listOf(note(artifact, DiagnosticSeverity.INFO, "module-unknown", "no module contains it"))

            else -> {
                val facts = ResourceFacts(artifact, location, module, sourceSet)
                readFacts(artifact, files) { content -> facts.of(reader.read(location, content, artifact.lineCount)) }
            }
        }
    }

    private fun readFacts(artifact: Artifact, files: WorkspaceFiles, facts: (ByteArray) -> List<Fact>): List<Fact> =
        try {
            facts(files.read(artifact.ref))
        } catch (failure: IOException) {
            listOf(note(artifact, DiagnosticSeverity.WARNING, "unreadable", "it could not be read: ${failure.message}"))
        }

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String) =
        Fact.OfDiagnostic(
            Diagnostic(severity, ReasonCode("android.resource.$code"), "${artifact.ref.path.value}: $message.", null),
        )
}
