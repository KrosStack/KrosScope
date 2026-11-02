package io.github.krosstack.kscope.service.map.tree

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin

/**
 * Finds the node of each symbol: the file that declares it, else the source of truth that generated it, else its
 * source set, else the node of its container, else the library it comes from. A chain that loops places nothing.
 */
internal class SymbolPlacer(private val tree: TreeIndex, private val symbols: List<Symbol>) {
    private val positions = HashMap<String, Int>(symbols.size * 2)
    private val nodes = IntArray(symbols.size) { UNSET }
    private val resolving = HashSet<Int>()

    fun place(): SymbolNodes {
        symbols.forEachIndexed { position, symbol -> positions[symbol.id.value] = position }
        symbols.indices.forEach(::nodeOf)
        return SymbolNodes(positions, nodes)
    }

    private fun nodeOf(position: Int): Int {
        if (nodes[position] == UNSET && resolving.add(position)) {
            nodes[position] = firstPlaced(symbols[position])
            resolving.remove(position)
        }
        return if (nodes[position] == UNSET) NONE else nodes[position]
    }

    private fun firstPlaced(symbol: Symbol): Int {
        val steps = sequenceOf(
            { declared(symbol) },
            { generatedFrom(symbol) },
            { inSourceSet(symbol) },
            { inContainer(symbol) },
            { fromLibrary(symbol) },
        )
        return steps.map { it() }.firstOrNull { it != NONE } ?: NONE
    }

    private fun declared(symbol: Symbol): Int =
        symbol.declaration?.let { tree.of(NodeRef.OfArtifact(it.artifact)) } ?: NONE

    private fun generatedFrom(symbol: Symbol): Int {
        val origin = symbol.origin as? SymbolOrigin.Generated ?: return NONE
        return when (val source = origin.sourceOfTruth) {
            null -> NONE
            is NodeRef.OfSymbol -> ofSymbol(source.id.value)
            else -> tree.of(source)
        }
    }

    private fun inSourceSet(symbol: Symbol): Int =
        symbol.scope.sourceSet?.let { tree.of(NodeRef.OfSourceSet(it)) } ?: NONE

    private fun inContainer(symbol: Symbol): Int = symbol.container?.let { ofSymbol(it.value) } ?: NONE

    private fun fromLibrary(symbol: Symbol): Int {
        val coordinate = (symbol.origin as? SymbolOrigin.Library)?.coordinate ?: return NONE
        return tree.of(NodeRef.OfExternal(coordinate))
    }

    private fun ofSymbol(id: String): Int = positions[id]?.let(::nodeOf) ?: NONE

    private companion object {
        const val UNSET = -2
    }
}
