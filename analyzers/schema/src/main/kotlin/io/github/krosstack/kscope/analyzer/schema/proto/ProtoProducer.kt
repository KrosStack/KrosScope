package io.github.krosstack.kscope.analyzer.schema.proto

import io.github.krosstack.kscope.analyzer.schema.Placement
import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.SymbolSite
import io.github.krosstack.kscope.analyzer.schema.TextIndex
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

private const val NOTE_PREFIX = "proto"

/** Turns each proto file into the symbols of the file, its messages, fields, enums, services and rpcs. */
internal class ProtoProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.proto", "0.2.0"),
        consumes = setOf(SchemaArtifactTypes.proto),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    private val reader = ProtoFileReader()

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

    private fun symbolsOf(artifact: Artifact, text: String, placement: Placement): List<Fact> =
        when (val reading = reader.read(artifact.ref.path.value, text)) {
            is ProtoReading.Failed ->
                listOf(fileNote(artifact, DiagnosticSeverity.WARNING, "$NOTE_PREFIX.syntax-error", reading.message))

            is ProtoReading.Parsed -> {
                val site = SymbolSite(artifact.ref, placement.scope, TextIndex(text))
                val packageName = reading.file.packageName
                val file = ProtoFileSymbol(artifact.ref.path.value, text, placement.identity).symbol(packageName)
                val declarations = ProtoDeclarations(reading.file, ProtoIdentities(placement.identity, packageName))
                (listOf(file) + declarations.symbols()).map { Fact.OfSymbol(it.toSymbol(site)) }
            }
        }

    private fun note(artifact: Artifact, severity: DiagnosticSeverity, code: String, message: String) =
        fileNote(artifact, severity, "$NOTE_PREFIX.$code", message)

    private fun readText(artifact: Artifact, files: WorkspaceFiles): String? = try {
        files.read(artifact.ref).toString(Charsets.UTF_8)
    } catch (_: IOException) {
        null
    }
}
