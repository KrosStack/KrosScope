package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlWorkspace
import io.github.krosstack.kscope.analyzer.android.aidl.syncSample
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange

internal const val PACKAGE = "sample.tasktide.sync.service"
internal const val APP = "sample.tasktide.app"

internal fun range(line: Int, start: Int, end: Int) = TextRange(line, start, line, end)

/** The AIDL side of the sync sample with its generated surface, and the Kotlin side that each test builds on it. */
internal class AidlScenario {
    private val workspace = AidlWorkspace(syncSample())
    private val aidlFacts = workspace.produce().flatMap { it.facts } + workspace.synthesize().facts
    val service = CodeSide(":svc")
    val app = CodeSide(":app")

    fun generated(qualifiedName: String): Symbol = aidlFacts.filterIsInstance<Fact.OfSymbol>()
        .map { it.value }
        .single { it.qualifiedName == qualifiedName && it.origin is SymbolOrigin.Generated }

    fun link(codeFacts: List<Fact> = service.facts + app.facts): Pair<AidlGraph, List<Relationship>> {
        val graph = AidlGraph(aidlFacts + codeFacts)
        val out = mutableListOf<Fact>()
        AidlLinker().link(graph) { out += it.facts }
        return graph to out.map { (it as Fact.OfRelationship).value }
    }

    /** The bindings of the given sub-kinds as one line each: sub-kind, contract, target, certainty and evidence. */
    fun lines(vararg subKinds: String): List<String> {
        val (graph, relationships) = link()
        val wanted = subKinds.toSet().ifEmpty { setOf("implementer", "client", "override") }
        return relationships.filter { it.subKind in wanted }.map { relationship ->
            val from = graph.symbol((relationship.from as NodeRef.OfSymbol).id)?.name
            "${relationship.subKind} $from -> ${outcome(graph, relationship.resolution)} ${relationship.evidenceClass}"
        }
    }

    private fun outcome(graph: AidlGraph, resolution: Resolution): String = when (resolution) {
        is Resolution.Resolved ->
            "${graph.symbol((resolution.target as NodeRef.OfSymbol).id)?.qualifiedName} ${resolution.certainty}"

        is Resolution.Unresolved -> "unresolved ${resolution.reason.value}"

        is Resolution.NotAnalysed -> "not-analysed ${resolution.reason.value}"
    }

    /** `class SyncService { val binder = object : ISyncService.Stub() }` and the callbacks it keeps; the binder. */
    fun serviceWithBinder(): Symbol {
        val syncService = service.symbol("$PACKAGE.SyncService")
        val binder = service.symbol("$PACKAGE.SyncService.binder", "kotlin.property", syncService)
        val callbacks = service.symbol("$PACKAGE.SyncService.callbacks", "kotlin.property", syncService)
        val stub = generated("$PACKAGE.ISyncService.Stub")
        service.edge(Edge(binder, RelationKind.TYPE_USES, generated("$PACKAGE.ISyncService"), range(22, 25, 37)))
        service.edge(Edge(binder, RelationKind.EXTENDS, stub, range(22, 38, 42), "anonymous"))
        service.edge(Edge(callbacks, RelationKind.TYPE_USES, generated("$PACKAGE.ISyncCallback"), range(17, 40, 53)))
        return binder
    }

    /** `class SyncServiceClient` holds an `ISyncService` and implements `ISyncCallback` as an object. */
    fun client() {
        val client = app.symbol("$APP.SyncServiceClient")
        val held = app.symbol("$APP.SyncServiceClient.service", "kotlin.property", client)
        val callback = app.symbol("$APP.SyncServiceClient.callback", "kotlin.property", client)
        val connection = app.symbol("$APP.SyncServiceClient.connection", "kotlin.property", client)
        val asInterface = generated("$PACKAGE.ISyncService.Stub.asInterface")
        app.edge(Edge(held, RelationKind.TYPE_USES, generated("$PACKAGE.ISyncService"), range(21, 26, 38)))
        app.edge(Edge(connection, RelationKind.REFERENCES, generated("$PACKAGE.ISyncService"), range(36, 31, 43)))
        app.edge(Edge(connection, RelationKind.CALLS, asInterface, range(36, 49, 60)))
        app.edge(Edge(callback, RelationKind.TYPE_USES, generated("$PACKAGE.ISyncCallback"), range(23, 37, 50)))
        val callbackStub = generated("$PACKAGE.ISyncCallback.Stub")
        app.edge(Edge(callback, RelationKind.EXTENDS, callbackStub, range(23, 51, 55), "anonymous"))
    }
}
