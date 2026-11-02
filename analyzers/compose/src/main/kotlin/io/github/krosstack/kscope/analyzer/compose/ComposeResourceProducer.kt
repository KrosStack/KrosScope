package io.github.krosstack.kscope.analyzer.compose

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
 * Turns each file of the resources of Compose Multiplatform into the resource symbols it defines: the strings,
 * plurals and string arrays of a `values` file and a drawable or font for each of those files, with one symbol for a
 * resource that several configurations define.
 */
internal class ComposeResourceProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.compose.resources", "0.1.0"),
        consumes = ComposeArtifactTypes.named,
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val reader = ComposeResourceReader()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(unit, artifact, request.files)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(unit: AnalysisUnit, artifact: Artifact, files: WorkspaceFiles): List<Fact> {
        val location = ComposeResourceLocations.of(artifact.ref.path.value)
        val module = unit.module
        val sourceSet = unit.sourceSet
        return when {
            location == null -> emptyList()

            module == null || sourceSet == null ->
                listOf(note(artifact, DiagnosticSeverity.INFO, "module-unknown", "no module contains it"))

            else -> readFacts(artifact, files) { content ->
                ComposeResourceFacts(artifact, location, module, sourceSet)
                    .of(reader.read(location, content, artifact.lineCount))
            }
        }
    }

    private fun readFacts(artifact: Artifact, files: WorkspaceFiles, facts: (ByteArray) -> List<Fact>): List<Fact> =
        try {
            facts(files.read(artifact.ref))
        } catch (failure: IOException) {
            val message = "it could not be read: ${failure.message}"
            listOf(note(artifact, DiagnosticSeverity.WARNING, "unreadable", message))
        }

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String) =
        Fact.OfDiagnostic(
            Diagnostic(severity, ReasonCode("compose.resource.$code"), "${artifact.ref.path.value}: $message.", null),
        )
}
