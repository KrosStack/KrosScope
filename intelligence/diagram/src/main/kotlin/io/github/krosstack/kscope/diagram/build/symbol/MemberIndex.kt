package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolId

internal data class TypeMembers(val ids: List<SymbolId>, val complete: Boolean)

internal class MemberIndex(private val reader: BoundedReader) {
    private val known = HashMap<SymbolId, TypeMembers?>()

    fun of(type: SymbolId): TypeMembers? {
        if (known.containsKey(type)) return known[type]
        return discover(type).also { known[type] = it }
    }

    private fun discover(type: SymbolId): TypeMembers? {
        val direct = reader.children(type) ?: return null
        val found = linkedSetOf(type)
        val pending = ArrayDeque(direct.memberIds().filter { found.add(it) })
        var complete = true
        while (pending.isNotEmpty()) {
            val nested = descend(pending.removeFirst(), found.size)
            complete = complete && nested != null
            pending += nested.orEmpty().filter { found.add(it) }
        }
        return TypeMembers(found.toList(), complete)
    }

    private fun descend(member: SymbolId, readsOwed: Int): List<SymbolId>? =
        if (reader.spare > readsOwed) reader.children(member)?.memberIds() else null

    private fun List<Symbol>.memberIds(): List<SymbolId> =
        filter { it.kind.category != SymbolCategory.TYPE }.map { it.id }
}
