package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader

internal const val REFERENCES_ATTRIBUTE = "aidl.references"

private val callsOfAsInterface = setOf(RelationKind.CALLS)
private val inheritanceAndImports = setOf(RelationKind.EXTENDS, RelationKind.IMPLEMENTS, RelationKind.IMPORTS)

/**
 * The classes that use an interface without implementing it: those that name its generated type or call
 * `Stub.asInterface`. Each client is the outermost class around the reference, because a reference sits in a member
 * and a reader wants the class. The type a class names only in front of `.Stub` in its supertype list does not make
 * it a client. An import counts for nothing alone: the use that needs it does.
 */
internal class ClientLinks(
    private val graph: GraphReader,
    private val contract: Symbol,
    private val surface: GeneratedSurface,
    supertypeEdges: List<Relationship>,
) {
    private val supertypeSites: List<SourceLocation> = supertypeEdges.mapNotNull { it.site }

    fun bindings(): List<Binding> {
        val references = typeReferences() + asInterfaceCalls()
        return references.groupBy { clientOf(it) }.mapNotNull { (client, group) ->
            client?.let { bindingOf(it, group) }
        }
    }

    private fun typeReferences(): List<Relationship> = surface.interfaceType
        ?.let { graph.incoming(NodeRef.OfSymbol(it.id)) }.orEmpty()
        .filter { it.kind !in inheritanceAndImports && !isSupertypeQualifier(it) }

    private fun asInterfaceCalls(): List<Relationship> =
        surface.asInterface?.let { graph.incoming(NodeRef.OfSymbol(it.id), callsOfAsInterface) }.orEmpty()

    private fun bindingOf(client: Symbol, references: List<Relationship>): Binding {
        val strongest = references.minBy { it.certainty() }
        return Binding(
            from = contract,
            target = NodeRef.OfSymbol(client.id),
            subKind = CLIENT,
            certainty = strongest.certainty(),
            scope = strongest.scope,
            attributes = mapOf(REFERENCES_ATTRIBUTE to references.size.toString()),
        )
    }

    private fun clientOf(reference: Relationship): Symbol? {
        val from = (reference.from as? NodeRef.OfSymbol)?.id ?: return null
        val reached = mutableSetOf<SymbolId>()
        val enclosing = generateSequence(graph.symbol(from)) { it.container?.let(graph::symbol) }
            .takeWhile { reached.add(it.id) }
            .toList()
        val symbol = enclosing.lastOrNull { it.kind.category == SymbolCategory.TYPE } ?: enclosing.firstOrNull()
        return symbol?.takeIf { it.origin is SymbolOrigin.Source && it.id != contract.id }
    }

    // The type in front of `.Stub` in `object : IFoo.Stub()` only spells the supertype.
    private fun isSupertypeQualifier(reference: Relationship): Boolean {
        val site = reference.site ?: return false
        return supertypeSites.any { supertype ->
            supertype.artifact == site.artifact &&
                supertype.range.startLine == site.range.endLine &&
                supertype.range.startColumn - site.range.endColumn in 0..1
        }
    }
}
