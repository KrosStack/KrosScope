package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.WebArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.WebKinds
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val ALL = Int.MAX_VALUE

/**
 * Joins every path that a Gradle task copies from to the producer of the web bundle in it, in any repository of the
 * workspace: the npm package whose bundler writes to the path, or the Kotlin/JS or Kotlin/Wasm module whose Gradle
 * build does. The dependency starts at the copy source and does not need the bundle on disk. Its evidence and its
 * certainty are those of the producer: by build model and probable for a directory that a config declares, by
 * convention and heuristic for a default directory. Several producers are an unresolved ambiguity, by convention; none
 * is no fact, as copying files that no bundle makes is common. Only the paths that lie in the checkout of a repository
 * of the workspace are looked at.
 */
internal class BundleConsumerLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.web.bundle-linker", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.buildScript, WebArtifactTypes.npmManifest),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val copies = graph.symbols(SymbolQuery(kindId = WebKinds.copySource.id, limit = ALL)).sortedBy { it.id.value }
        if (copies.isEmpty()) return
        val producers = BundleProducers(graph)
        val facts = copies.mapNotNull { dependency(it, producers.of(it)) }.map { Fact.OfRelationship(it) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }

    private fun dependency(copy: Symbol, producers: List<BundleProducer>): Relationship? {
        val producer = producers.singleOrNull()
        val resolution = when {
            producers.isEmpty() -> return null
            producer != null -> Resolution.Resolved(producer.target, producer.certainty)
            else -> Resolution.Unresolved(copy.signature ?: copy.name, ReasonCode(WebKinds.AMBIGUOUS_PRODUCER))
        }
        return Relationship(
            from = NodeRef.OfSymbol(copy.id),
            kind = RelationKind.DEPENDS_ON,
            subKind = WebKinds.WEB_BUNDLE_SUB_KIND,
            resolution = resolution,
            evidenceClass = producer?.evidence ?: EvidenceClass.CONVENTION,
            site = copy.declaration,
            scope = copy.scope,
            attributes = copy.attributes[WebKinds.COPY_INTO_ATTRIBUTE]
                ?.let { mapOf(WebKinds.BUNDLE_INTO_ATTRIBUTE to it) }.orEmpty(),
        )
    }
}
