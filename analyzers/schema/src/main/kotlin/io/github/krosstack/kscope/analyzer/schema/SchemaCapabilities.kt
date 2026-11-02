package io.github.krosstack.kscope.analyzer.schema

import io.github.krosstack.kscope.analyzer.schema.generator.GeneratorInputSynthesizer
import io.github.krosstack.kscope.analyzer.schema.ktor.KtorRouteProducer
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiDocumentDetector
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiProducer
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoProducer
import io.github.krosstack.kscope.analyzer.schema.proto.ProtoSourceDetector
import io.github.krosstack.kscope.analyzer.schema.proto.WireKotlinSynthesizer
import io.github.krosstack.kscope.analyzer.schema.room.RoomLinker
import io.github.krosstack.kscope.analyzer.schema.room.RoomSchemaDetector
import io.github.krosstack.kscope.analyzer.schema.room.RoomSchemaProducer
import io.github.krosstack.kscope.analyzer.schema.spec.OpenApiRouteLinker
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightLinker
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightProducer
import io.github.krosstack.kscope.analyzer.schema.sqldelight.SqlDelightSourceDetector
import io.github.krosstack.kscope.analyzer.schema.sqldelight.synthesis.SqlDelightSynthesizer
import io.github.krosstack.kscope.spi.ArtifactDetector
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SurfaceSynthesizer

/**
 * The capabilities for schema artifacts: Protocol Buffers with Wire, SQLDelight, Room, and OpenAPI documents with the
 * Ktor routes that implement their operations and the generators that build clients of them.
 */
public object SchemaCapabilities {
    public fun detectors(): List<ArtifactDetector> =
        listOf(OpenApiDocumentDetector(), ProtoSourceDetector(), RoomSchemaDetector(), SqlDelightSourceDetector())

    public fun producers(): List<FactProducer> =
        listOf(KtorRouteProducer(), OpenApiProducer(), ProtoProducer(), RoomSchemaProducer(), SqlDelightProducer())

    public fun synthesizers(): List<SurfaceSynthesizer> =
        listOf(GeneratorInputSynthesizer(), WireKotlinSynthesizer(), SqlDelightSynthesizer())

    public fun linkers(): List<BoundaryLinker> = listOf(OpenApiRouteLinker(), RoomLinker(), SqlDelightLinker())
}
