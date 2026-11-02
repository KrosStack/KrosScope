package io.github.krosstack.kscope.analyzer.schema.room

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * Binds the parts of a Room database: the exported schema to the entity classes and their properties, and a database
 * to its entities, its DAOs and its schema file. The relationships between code are taken from what the compiler
 * resolved, and a DAO method is joined to the tables its `@Query` names by the text of the query.
 */
internal class RoomLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.room-linker", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.roomSchema),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val classes = RoomClasses(graph)
        if (classes.isEmpty) return
        val components = RoomComponentBindings(graph, classes)
        val relationships = components.all() + RoomSchemaBindings(graph, classes, components).all()
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        val facts = relationships.map { Fact.OfRelationship(it) }
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}
