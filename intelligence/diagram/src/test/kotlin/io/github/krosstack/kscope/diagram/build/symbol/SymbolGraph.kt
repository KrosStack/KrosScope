package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.diagram.DiagramGraph
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.spi.graph.WorkspaceStructure

internal class SymbolGraph(private val world: SymbolWorld, private val mix: Int = 0) : GraphReader by DiagramGraph() {
    private val calls = mutableListOf<String>()
    private val rows = mutableListOf<Relationship>()

    val log: List<String> get() = calls

    val served: Set<Relationship> get() = rows.toSet()

    fun count(prefix: String): Int = calls.count { it.startsWith(prefix) }

    override fun structure(): WorkspaceStructure = world.structure()

    override fun symbol(id: SymbolId): Symbol? {
        calls += "symbol:${id.value}"
        return world.symbols[id]
    }

    override fun symbols(query: SymbolQuery): List<Symbol> {
        calls += "symbols:${query.container?.value ?: query.qualifiedName ?: query.name}"
        if (query.limit == Int.MAX_VALUE) {
            calls += "forbidden:symbols"
            error("forbidden: symbols without a limit")
        }
        return reorder(
            world.symbols.values.filter { symbol ->
                (query.name == null || symbol.name == query.name) &&
                    (query.qualifiedName == null || symbol.qualifiedName == query.qualifiedName) &&
                    (query.container == null || symbol.container == query.container)
            }.take(query.limit),
        )
    }

    override fun outgoing(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> {
        calls += "outgoing:${(node as NodeRef.OfSymbol).id.value}"
        return reorder(world.relationships.filter { it.from == node && (kinds.isEmpty() || it.kind in kinds) })
            .also { rows += it }
    }

    override fun incoming(node: NodeRef, kinds: Set<RelationKind>): List<Relationship> {
        calls += "incoming:${(node as NodeRef.OfSymbol).id.value}"
        return reorder(
            world.relationships.filter {
                (it.resolution as? Resolution.Resolved)?.target == node && (kinds.isEmpty() || it.kind in kinds)
            },
        ).also { rows += it }
    }

    override fun relationships(query: RelationshipQuery): List<Relationship> {
        calls += "forbidden:relationships"
        error("forbidden: relationships over the whole graph")
    }

    private fun <T> reorder(items: List<T>): List<T> = when (mix) {
        0 -> items
        1 -> items.reversed()
        else -> items.filterIndexed { index, _ -> index % 2 == 1 } + items.filterIndexed { index, _ -> index % 2 == 0 }
    }
}
