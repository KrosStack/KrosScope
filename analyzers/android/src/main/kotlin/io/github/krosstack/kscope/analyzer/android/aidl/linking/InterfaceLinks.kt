package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.stub.StubRole
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.graph.GraphReader

internal const val SUPERTYPE_ATTRIBUTE = "aidl.supertype"

private val inheritance = setOf(RelationKind.EXTENDS, RelationKind.IMPLEMENTS)
private val overrides = setOf(RelationKind.OVERRIDES)

/** A relationship that makes a class or an object a subtype of part of the generated surface, and which part. */
internal class SupertypeEdge(val role: String, val edge: Relationship)

/**
 * The bindings of one interface: its implementers, its clients and the overrides of its methods. They come from
 * compiler relationships into the generated surface; the contract is reached through the generator's convention.
 * Overrides in object expressions are inferred, see [AnonymousOverrides].
 */
internal class InterfaceLinks(private val graph: GraphReader, private val contract: Symbol) {
    private val surface = GeneratedSurface(graph, contract)

    fun relationships(): List<Relationship> {
        val supertypes = supertypeEdges()
        val clients = ClientLinks(graph, contract, surface, supertypes.map { it.edge }).bindings()
        val stated = overrideBindings()
        val inferred = AnonymousOverrides(surface.methods()).bindings(supertypes, stated)
        return (implementerBindings(supertypes) + clients + stated + inferred).map { bindingOf(it) }
    }

    private fun supertypeEdges(): List<SupertypeEdge> {
        val supertypes = listOf(
            StubRole.STUB.value to surface.stub,
            StubRole.DEFAULT.value to surface.defaultImplementation,
            StubRole.INTERFACE.value to surface.interfaceType,
        )
        return supertypes.flatMap { (role, symbol) ->
            val edges = symbol?.let { graph.incoming(NodeRef.OfSymbol(it.id), inheritance) }.orEmpty()
            edges.map { SupertypeEdge(role, it) }
        }
    }

    private fun implementerBindings(edges: List<SupertypeEdge>): List<Binding> =
        edges.filter { it.edge.from is NodeRef.OfSymbol }.groupBy { it.edge.from }.map { (implementer, group) ->
            val strongest = group.minBy { it.edge.certainty() }
            Binding(
                from = contract,
                target = implementer,
                subKind = IMPLEMENTER,
                certainty = strongest.edge.certainty(),
                scope = strongest.edge.scope,
                attributes = mapOf(SUPERTYPE_ATTRIBUTE to strongest.role),
            )
        }

    private fun overrideBindings(): List<Binding> = surface.methods().flatMap { method ->
        val generated = surface.generatedMethodOf(method)
        val edges = generated?.let { graph.incoming(NodeRef.OfSymbol(it.id), overrides) }.orEmpty()
        edges.filter { it.from is NodeRef.OfSymbol }.groupBy { it.from }.map { (override, group) ->
            val strongest = group.minBy { it.certainty() }
            Binding(method, override, OVERRIDE, strongest.certainty(), strongest.scope)
        }
    }
}
