package io.github.krosstack.kscope.analyzer.schema.room

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
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import java.io.IOException

internal object RoomKinds {
    val entity = SymbolKind("room.schema.entity", SymbolCategory.SCHEMA)
    val field = SymbolKind("room.schema.field", SymbolCategory.SCHEMA)
}

/**
 * The name of the database class an exported schema belongs to: Room exports to
 * `<schema directory>/<class name>/<version>.json`.
 */
internal fun databaseNameOf(artifact: Artifact): String =
    artifact.ref.path.value.split('/').let { it.getOrElse(it.size - 2) { "" } }

/** Turns each schema file that Room exports into a symbol per entity and per field, which are generated artifacts. */
internal class RoomSchemaProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.room", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.roomSchema),
        capabilities = setOf(AnalyzerCapability.DECLARATIONS),
        granularity = OwnershipKind.ARTIFACT,
    )

    override fun produce(request: ProduceRequest, sink: FactSink) {
        request.units.forEach { unit ->
            val artifact = unit.artifacts.single()
            val facts = factsOf(artifact, request)
            sink.accept(FactBatch(descriptor.producer, unit.owner, Fingerprint(artifact.contentHash.value), facts))
        }
    }

    private fun factsOf(artifact: Artifact, request: ProduceRequest): List<Fact> {
        val placement = Placement.of(artifact, request.model)
        val text = try {
            request.files.read(artifact.ref).toString(Charsets.UTF_8)
        } catch (_: IOException) {
            null
        }
        val schema = text?.let(RoomSchemaReader::read)
        return when {
            placement == null ->
                listOf(fileNote(artifact, DiagnosticSeverity.INFO, "room.module-unknown", "no module contains it"))

            text == null || schema == null ->
                listOf(fileNote(artifact, DiagnosticSeverity.WARNING, "room.schema-unreadable", "it is not an export"))

            else -> {
                val site = SymbolSite(artifact.ref, placement.scope, TextIndex(text))
                val declarations = RoomDeclarations(schema, databaseNameOf(artifact), placement.identity)
                declarations.symbols().map { Fact.OfSymbol(it.toSymbol(site)) }
            }
        }
    }
}
