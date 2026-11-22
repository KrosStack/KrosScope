package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId

private val calls = setOf(RelationKind.CALLS)

private val sourceOrder = compareBy<Relationship>(
    { it.site?.artifact?.path?.value },
    { it.site?.range?.startLine },
    { it.site?.range?.startColumn },
    { it.site?.range?.endLine },
    { it.site?.range?.endColumn },
    { it.resolution.toString() },
    { it.subKind },
)

internal class CallLevel(private val reader: BoundedReader) : FlowLevel {
    override val title: String = "Call flow of"

    override val spent: Boolean get() = reader.exhausted

    override val membersNotRead: Int = 0

    override val legendNotes: List<String> = emptyList()

    override fun outgoing(unit: SymbolId, share: Share): Reading {
        val made = reader.outgoing(unit, calls)
        val positions = positions(made)
        return made.map { call -> call.outcome(unit, call.targetSymbol(), positions[call]) }.toReading(!spent)
    }

    override fun incoming(unit: SymbolId, share: Share): Reading =
        reader.incoming(unit, calls).map { call -> call.outcome(call.sourceSymbol(), unit) }.toReading(!spent)

    override fun admits(unit: SymbolId): Boolean = reader.symbol(unit)?.kind?.category == SymbolCategory.CALLABLE

    override fun draws(unit: SymbolId): Boolean = true

    override fun face(symbol: Symbol): NodeFace {
        val owner = symbol.container?.let { reader.typeOf(it) }?.let { reader.described(it) }
        val group = owner?.let { GroupKey("type:${it.id.value}", it.qualifiedName ?: it.name) } ?: fileGroup(symbol)
        return NodeFace(symbol.signature ?: symbol.name, group)
    }

    override fun rejected(unit: SymbolId): String? =
        reader.known(unit)?.takeIf { it.kind.category != SymbolCategory.CALLABLE }?.kind?.id

    override fun refusalOf(unit: SymbolId): Unseen =
        if (reader.known(unit) == null) Unseen.WITHOUT_SYMBOL else Unseen.NOT_CALLABLE

    private fun fileGroup(symbol: Symbol): GroupKey? = symbol.declaration?.artifact?.let { artifact ->
        GroupKey("file:${artifact.repository.value}\n${artifact.path.value}", reader.locations.pathOf(artifact))
    }

    private fun positions(made: List<Relationship>): Map<Relationship, Int> =
        made.filter { it.site != null }.distinct().sortedWith(sourceOrder).withIndex()
            .associate { it.value to it.index + 1 }
}
