package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.WorkspaceLocations
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private const val CHILD_LIMIT = 10_000

internal class BoundedReader(private val reader: GraphReader, private val budget: ReadBudget) {
    private val symbols = HashMap<SymbolId, Symbol?>()
    private val idTypes = HashMap<SymbolId, IdType>()

    val locations: WorkspaceLocations by lazy { WorkspaceLocations(reader.structure().repositories) }

    val exhausted: Boolean get() = budget.exhausted

    val spare: Int get() = budget.remaining

    fun remember(symbol: Symbol) {
        symbols[symbol.id] = symbol
    }

    fun symbol(id: SymbolId): Symbol? = when {
        symbols.containsKey(id) -> symbols[id]
        budget.take() -> reader.symbol(id).also { symbols[id] = it }
        else -> null
    }

    fun described(id: SymbolId): Symbol? =
        if (symbols.containsKey(id)) symbols[id] else reader.symbol(id).also { symbols[id] = it }

    fun known(id: SymbolId): Symbol? = symbols[id]

    /** Reads every kind and filters here: a kind filter makes the store scan its kind index instead of the node. */
    fun outgoing(id: SymbolId, kinds: Set<RelationKind>): List<Relationship> {
        if (!budget.take()) return emptyList()
        return reader.outgoing(NodeRef.OfSymbol(id), emptySet()).filter { it.kind in kinds }
    }

    fun incoming(id: SymbolId, kinds: Set<RelationKind>): List<Relationship> {
        if (!budget.take()) return emptyList()
        return reader.incoming(NodeRef.OfSymbol(id), emptySet()).filter { it.kind in kinds }
    }

    fun children(id: SymbolId): List<Symbol>? {
        if (!budget.take()) return null
        val found = reader.symbols(SymbolQuery(container = id, limit = CHILD_LIMIT + 1))
        return if (found.size > CHILD_LIMIT) null else found.distinctBy { it.id }.sortedBy { it.id.value }
    }

    fun modulePaths(): Map<String, String> = reader.structure().modules.associate { it.id.value to it.path }

    fun typeOf(id: SymbolId): SymbolId? = when (val named = idTypes.getOrPut(id) { EnclosingType.of(id) }) {
        is IdType.Named -> named.type
        IdType.Absent -> null
        IdType.Unstated -> typeThroughContainers(id, emptySet())
    }

    private fun typeThroughContainers(id: SymbolId, climbed: Set<SymbolId>): SymbolId? {
        val symbol = symbol(id)
        return when {
            symbol == null || id in climbed -> null
            symbol.kind.category == SymbolCategory.TYPE -> id
            else -> symbol.container?.let { typeThroughContainers(it, climbed + id) }
        }
    }
}
