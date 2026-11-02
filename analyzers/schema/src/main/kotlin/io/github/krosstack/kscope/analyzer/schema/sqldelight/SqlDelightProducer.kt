package io.github.krosstack.kscope.analyzer.schema.sqldelight

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

/**
 * Turns each `.sq` file into the symbols of its tables, columns and queries, and the symbolic references from its
 * queries to the tables they read and write. A migration file declares nothing that a symbol stands for.
 */
internal class SqlDelightProducer : FactProducer {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.sqldelight", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.sqldelight),
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
        val source = SqSource.read(artifact, request.model, request.files)
        return when {
            source != null -> factsOf(source)

            Placement.of(artifact, request.model) == null ->
                listOf(fileNote(artifact, DiagnosticSeverity.INFO, "sqldelight.module-unknown", "no module owns it"))

            else -> emptyList()
        }
    }

    private fun factsOf(source: SqSource): List<Fact> {
        val site = SymbolSite(source.artifact.ref, source.placement.scope, TextIndex(source.text))
        val symbols = SqlDelightDeclarations(source).symbols().map { Fact.OfSymbol(it.toSymbol(site)) }
        val references = SqlDelightTableReferences(source, site).all().map { Fact.OfSymbolicReference(it) }
        return symbols + references
    }
}
