package io.github.krosstack.kscope.analyzer.schema.openapi

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
import io.github.krosstack.kscope.analyzer.schema.document.YamlReader
import io.github.krosstack.kscope.analyzer.schema.fileNote
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException

private const val NOTE_PREFIX = "openapi"

/** Turns each OpenAPI document into the symbols of the document, its operations and its schemas. */
internal class OpenApiProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.openapi", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.openApi),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val reader = OpenApiReader()

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(artifact, request)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(artifact: Artifact, request: ProduceRequest): List<Fact> {
        val placement = Placement.of(artifact, request.model)
        val text = readText(artifact, request.files)
        return when {
            placement == null ->
                listOf(note(artifact, DiagnosticSeverity.INFO, "module-unknown", "no module contains it"))

            text == null ->
                listOf(note(artifact, DiagnosticSeverity.WARNING, "unreadable", "it could not be read"))

            else -> symbolsOf(artifact, text, placement)
        }
    }

    private fun symbolsOf(artifact: Artifact, text: String, placement: Placement): List<Fact> {
        val document = reader.read(YamlReader(text).read())
            ?: return listOf(note(artifact, DiagnosticSeverity.WARNING, "unreadable-document", "it is no document"))
        val site = SymbolSite(artifact.ref, placement.scope, TextIndex(text))
        val ids = OpenApiIdentities(placement.identity, artifact.ref.path.value)
        val symbols = OpenApiDeclarations(document, ids).symbols().distinctBy { it.id }
        return symbols.map { Fact.OfSymbol(it.toSymbol(site)) }
    }

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String) =
        fileNote(artifact, severity, "$NOTE_PREFIX.$code", message)

    private fun readText(artifact: Artifact, files: WorkspaceFiles): String? = try {
        files.read(artifact.ref).toString(Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }
}
