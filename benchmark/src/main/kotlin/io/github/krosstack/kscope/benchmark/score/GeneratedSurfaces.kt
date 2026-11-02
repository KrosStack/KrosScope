package io.github.krosstack.kscope.benchmark.score

import io.github.krosstack.kscope.benchmark.graph.GraphIndex
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin

private val codeTypes = setOf("kotlin.source", "java.source")
private const val TYPE_SOURCE = "generated.type-source"

/** A place in code that uses a symbol a generator made: the relationship, its file and the symbol it targets. */
internal class SurfaceUse(val relationship: Relationship, val path: String, val target: Symbol)

/** A hop from a source of truth to a generated symbol, and whether the use reaches the type itself or only a member. */
internal class SourceHop(val source: Symbol, val hop: Relationship, val isNamed: Boolean)

/**
 * The uses in code of generated symbols, joined to the sources of truth they come from: the `GENERATES` relationship
 * from a message, a table or a query to the generated class or function is the first hop of such a path, and the
 * reference from the code is the second.
 */
internal class GeneratedSurfaces(private val index: GraphIndex) {
    private val hopsByGenerated: Map<SymbolId, List<Relationship>> = index.relationships
        .filter { it.kind == RelationKind.GENERATES && it.from is NodeRef.OfSymbol }
        .mapNotNull { hop -> (index.resolvedTarget(hop) as? NodeRef.OfSymbol)?.let { it.id to hop } }
        .groupBy({ it.first }, { it.second })

    val uses: List<SurfaceUse> = index.relationships.mapNotNull(::useOf)

    /** The sources of kind [sourceKinds] of the generated type that [target] is, or is a member of. */
    fun typeSourcesOf(target: Symbol, sourceKinds: Set<String>): List<SourceHop> {
        val reached = mutableSetOf<SymbolId>()
        val chain = generateSequence(target) { symbol -> symbol.container?.let(index.symbolsById::get) }
            .takeWhile { reached.add(it.id) }
        val owner = chain.firstOrNull { hopsOf(it, sourceKinds).isNotEmpty() } ?: return emptyList()
        val isConstructor = target.kind.id.endsWith(".constructor") && target.container == owner.id
        val isNamed = owner.id == target.id || isConstructor
        return hopsOf(owner, sourceKinds).map { (hop, source) -> SourceHop(source, hop, isNamed) }
    }

    /** The sources of kind [sourceKinds] that the declared type of [target] is generated from, as a generator says. */
    fun typedSourcesOf(target: Symbol, sourceKinds: Set<String>): List<Symbol> =
        listOfNotNull(target.attributes[TYPE_SOURCE]?.let { index.symbolsById[SymbolId(it)] })
            .filter { it.kind.id in sourceKinds }

    /** The sources of kind [sourceKinds] that generated [target] itself, such as the query of a function. */
    fun sourcesOf(target: Symbol, sourceKinds: Set<String>): List<SourceHop> =
        hopsOf(target, sourceKinds).map { (hop, source) -> SourceHop(source, hop, true) }

    private fun hopsOf(symbol: Symbol, sourceKinds: Set<String>): List<Pair<Relationship, Symbol>> =
        hopsByGenerated[symbol.id].orEmpty().mapNotNull { hop ->
            val source = (hop.from as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }
            source?.takeIf { it.kind.id in sourceKinds }?.let { hop to it }
        }

    private fun useOf(relationship: Relationship): SurfaceUse? {
        val path = relationship.site?.artifact?.path?.value
        val target = (index.resolvedTarget(relationship) as? NodeRef.OfSymbol)?.let { index.symbolsById[it.id] }
        val isCode = path != null && index.artifactsByPath[path]?.type?.id in codeTypes
        val isGenerated = target?.origin is SymbolOrigin.Generated
        return if (isCode && isGenerated && relationship.kind != RelationKind.GENERATES) {
            SurfaceUse(relationship, checkNotNull(path), checkNotNull(target))
        } else {
            null
        }
    }
}
