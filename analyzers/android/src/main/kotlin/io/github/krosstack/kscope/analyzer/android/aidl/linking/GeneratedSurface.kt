package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlSymbols
import io.github.krosstack.kscope.analyzer.android.aidl.stub.ROLE_ATTRIBUTE
import io.github.krosstack.kscope.analyzer.android.aidl.stub.StubRole
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

/** The generated symbols of one AIDL declaration, found through the `GENERATES` edges that point at them. */
internal class GeneratedSurface(private val graph: GraphReader, private val contract: Symbol) {
    private val byRole: Map<String?, Symbol> = generatedFrom(contract.id).associateBy { it.attributes[ROLE_ATTRIBUTE] }

    val interfaceType: Symbol? get() = byRole[StubRole.INTERFACE.value]
    val stub: Symbol? get() = byRole[StubRole.STUB.value]

    /** The generated `Default`, a member of the interface. */
    val defaultImplementation: Symbol? by lazy { memberOf(interfaceType, StubRole.DEFAULT) }

    /** The generated `asInterface`, a member of the stub. */
    val asInterface: Symbol? by lazy { memberOf(stub, StubRole.AS_INTERFACE) }

    private fun memberOf(holder: Symbol?, role: StubRole): Symbol? = holder
        ?.let { graph.symbols(SymbolQuery(container = it.id, limit = ALL)) }
        ?.firstOrNull { it.attributes[ROLE_ATTRIBUTE] == role.value }

    fun generatedMethodOf(method: Symbol): Symbol? =
        generatedFrom(method.id).firstOrNull { it.attributes[ROLE_ATTRIBUTE] == StubRole.METHOD.value }

    fun methods(): List<Symbol> = graph.symbols(SymbolQuery(container = contract.id, limit = ALL))
        .filter { it.kind.id == AidlSymbols.METHOD_KIND }

    private fun generatedFrom(id: SymbolId): List<Symbol> =
        graph.outgoing(NodeRef.OfSymbol(id), setOf(RelationKind.GENERATES))
            .mapNotNull { ((it.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.id }
            .mapNotNull(graph::symbol)
}
