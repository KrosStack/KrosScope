package io.github.krosstack.kscope.analyzer.schema.spec

import io.github.krosstack.kscope.analyzer.schema.SchemaArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.ktor.KtorKinds
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiKinds
import io.github.krosstack.kscope.model.Certainty
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
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val OPENAPI_ROUTE_SUB_KIND = "openapi-route"
internal const val AMBIGUOUS_IMPLEMENTATION = "spec.ambiguous-implementation"
private const val ALL = Int.MAX_VALUE

/**
 * Joins every operation of an OpenAPI document to the Ktor route that implements it, in any repository of the
 * workspace. A route is the implementation when its method is the operation's and its path is the operation's with
 * the parameters taken as placeholders, whatever their names. When no route has the path of the document, the
 * relative `servers` paths of the document are tried as prefixes of it. The names are a convention and a route that
 * is declared by the match of its text may not be the one that a request reaches, so the evidence is `convention` and
 * the binding is probable. Several routes are an unresolved ambiguity; none is no fact, as most operations of a
 * workspace may be served by code that is not in it.
 */
internal class OpenApiRouteLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.schema.openapi-route-linker", "0.1.0"),
        consumes = setOf(SchemaArtifactTypes.openApi),
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val operations = symbolsOf(graph, OpenApiKinds.OPERATION_KIND)
        if (operations.isEmpty()) return
        val routes = RouteIndex(symbolsOf(graph, KtorKinds.ROUTE_KIND))
        val prefixes = serverPrefixes(symbolsOf(graph, OpenApiKinds.document.id))
        val bindings = operations.mapNotNull { binding(it, candidatesOf(it, routes, prefixes)) }
        val facts = bindings.map { Fact.OfRelationship(it) }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }

    private fun symbolsOf(graph: GraphReader, kindId: String): List<Symbol> =
        graph.symbols(SymbolQuery(kindId = kindId, limit = ALL)).sortedBy { it.id.value }

    private fun serverPrefixes(documents: List<Symbol>): Map<SymbolId, List<String>> = documents.associate {
        it.id to it.attributes[OpenApiKinds.SERVERS_ATTRIBUTE].orEmpty().split(' ').filter(String::isNotEmpty)
    }

    private fun candidatesOf(
        operation: Symbol,
        routes: RouteIndex,
        prefixes: Map<SymbolId, List<String>>,
    ): List<Symbol> {
        val method = operation.attributes[OpenApiKinds.METHOD_ATTRIBUTE].orEmpty()
        val path = operation.attributes[OpenApiKinds.PATH_ATTRIBUTE].orEmpty()
        val direct = routes.find(method, path)
        val servers = operation.container?.let { prefixes[it] }.orEmpty()
        return direct.ifEmpty { servers.flatMap { routes.find(method, "$it/$path") }.distinctBy { it.id } }
    }

    private fun binding(operation: Symbol, candidates: List<Symbol>): Relationship? {
        val resolution = when (candidates.size) {
            0 -> return null
            1 -> Resolution.Resolved(NodeRef.OfSymbol(candidates.single().id), Certainty.PROBABLE)
            else -> Resolution.Unresolved(operation.signature ?: operation.name, ReasonCode(AMBIGUOUS_IMPLEMENTATION))
        }
        return Relationship(
            from = NodeRef.OfSymbol(operation.id),
            kind = RelationKind.BINDS,
            subKind = OPENAPI_ROUTE_SUB_KIND,
            resolution = resolution,
            evidenceClass = EvidenceClass.CONVENTION,
            site = operation.declaration,
            scope = operation.scope,
        )
    }
}
