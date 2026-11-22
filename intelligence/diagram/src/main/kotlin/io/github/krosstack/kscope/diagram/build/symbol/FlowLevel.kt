package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolId

internal data class GroupKey(val id: String, val label: String)

internal data class NodeFace(val label: String, val group: GroupKey?)

internal interface FlowLevel {
    val title: String

    val spent: Boolean

    val membersNotRead: Int

    val legendNotes: List<String>

    fun outgoing(unit: SymbolId, share: Share): Reading

    fun incoming(unit: SymbolId, share: Share): Reading

    fun admits(unit: SymbolId): Boolean

    fun draws(unit: SymbolId): Boolean

    fun face(symbol: Symbol): NodeFace

    fun rejected(unit: SymbolId): String?

    fun refusalOf(unit: SymbolId): Unseen
}
