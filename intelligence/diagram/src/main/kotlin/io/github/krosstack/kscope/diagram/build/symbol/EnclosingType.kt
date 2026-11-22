package io.github.krosstack.kscope.diagram.build.symbol

import io.github.krosstack.kscope.model.DescriptorSegment
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolIdentity

internal sealed interface IdType {
    data class Named(val type: SymbolId) : IdType

    data object Absent : IdType

    data object Unstated : IdType
}

internal object EnclosingType {
    fun of(id: SymbolId): IdType = when (val parsed = id.identity()) {
        is ParsedSymbolId.Invalid -> IdType.Unstated
        is ParsedSymbolId.Valid -> named(parsed.identity)
    }

    private fun named(identity: SymbolIdentity): IdType {
        val segments = identity.descriptor.segments
        val last = segments.indexOfLast { it is DescriptorSegment.Type }
        if (last < 0) return IdType.Absent
        val type = identity.copy(descriptor = SymbolDescriptor(segments.take(last + 1)))
        return IdType.Named(type.toSymbolId())
    }
}
