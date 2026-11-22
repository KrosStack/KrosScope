package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId

private val typeKinds = setOf(
    RelationKind.CALLS,
    RelationKind.REFERENCES,
    RelationKind.TYPE_USES,
    RelationKind.EXTENDS,
    RelationKind.IMPLEMENTS,
)

private val dependencyKinds = setOf(RelationKind.CALLS, RelationKind.REFERENCES, RelationKind.TYPE_USES)

internal class TypeLevel(private val reader: BoundedReader) : FlowLevel {
    private val members = MemberIndex(reader)
    private val unreadMembers = linkedSetOf<SymbolId>()
    private val modules: Map<String, String> by lazy { reader.modulePaths() }

    override val title: String = "Type relations of"

    override val spent: Boolean get() = reader.exhausted

    override val membersNotRead: Int get() = unreadMembers.size

    override val legendNotes: List<String> =
        listOf("depends: calls, references and type-uses between two types")

    override fun outgoing(unit: SymbolId, share: Share): Reading = read(unit, share) { member ->
        reader.outgoing(member, typeKinds).map { relation -> leaving(unit, relation) }
    }

    override fun incoming(unit: SymbolId, share: Share): Reading = read(unit, share) { member ->
        reader.incoming(member, typeKinds).map { relation -> entering(unit, relation) }
    }

    override fun admits(unit: SymbolId): Boolean = reader.described(unit)?.kind?.category == SymbolCategory.TYPE

    override fun rejected(unit: SymbolId): String? = null

    override fun draws(unit: SymbolId): Boolean = reader.described(unit)?.kind?.category == SymbolCategory.TYPE

    override fun face(symbol: Symbol): NodeFace {
        val unit = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit
        val group = unit?.let { GroupKey("module:$it", modules[it] ?: it) }
        return NodeFace(symbol.qualifiedName ?: symbol.name, group)
    }

    override fun refusalOf(unit: SymbolId): Unseen = Unseen.NOT_IN_TYPE

    private fun read(unit: SymbolId, share: Share, outcomesOf: (SymbolId) -> List<Outcome>): Reading {
        val found = members.of(unit) ?: return Reading(emptyList(), false)
        var allowance = maxOf(1, reader.spare / share.parts)
        val outcomes = mutableListOf<Outcome>()
        var missed = false
        for (member in found.ids) {
            val made = if (allowance > 0) outcomesOf(member) else emptyList()
            allowance -= 1
            if (allowance < 0 || reader.exhausted) {
                missed = true
                if (member != unit) unreadMembers += member
                outcomes += made.map { it.origin.lost(Unseen.STOPPED) }
            } else {
                outcomes += made
            }
        }
        return outcomes.toReading(found.complete && !missed)
    }

    private fun leaving(unit: SymbolId, relation: Relationship): Outcome {
        val symbol = relation.targetSymbol()
        val type = symbol?.let { reader.typeOf(it) }
        return when {
            symbol != null && type == null -> relation.lost(Unseen.NOT_IN_TYPE)
            type == unit -> relation.lost(Unseen.WITHIN_TYPE)
            else -> relation.outcome(unit, type, merged = type != null && relation.kind in dependencyKinds)
        }
    }

    private fun entering(unit: SymbolId, relation: Relationship): Outcome {
        val source = relation.sourceSymbol()
        val type = source?.let { reader.typeOf(it) }
        return when {
            source != null && type == null -> relation.lost(Unseen.NOT_IN_TYPE)
            type == unit -> relation.lost(Unseen.WITHIN_TYPE)
            else -> relation.outcome(type, unit, merged = relation.kind in dependencyKinds)
        }
    }
}
