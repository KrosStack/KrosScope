package io.github.krosstack.kscope.benchmark.schema

import io.github.krosstack.kscope.diagram.DiagramNode
import io.github.krosstack.kscope.diagram.Omission
import kotlinx.serialization.ExperimentalSerializationApi

@OptIn(ExperimentalSerializationApi::class)
internal object FieldDescriptions {
    private val byType = mapOf(
        DiagramNode.serializer().descriptor.serialName to mapOf(
            "symbolId" to "The id of the symbol this node stands for; symbol diagrams only.",
        ),
        Omission.serializer().descriptor.serialName to mapOf(
            "notExpanded" to "Symbols not expanded: the walk stopped before it read their links. Counts symbols.",
            "membersNotRead" to "Members not read: the read limit left their links unread. Counts members.",
            "callsNotDrawn" to "Calls to targets that are no callable, not drawn, by target kind. " +
                "A by-kind view of part of notCallable.",
            "notInType" to
                "Links with an end in no type: a top-level function, a file facade or a class the index lacks.",
            "notCallable" to "Links with a caller or target that is no callable: the call level draws callables only.",
            "withoutSymbol" to "Links with an end that has no symbol: an external library or an id the index lacks.",
            "withinType" to "Links between two members of one type: a type has no edge to itself.",
            "beyondDepth" to
                "Links from a symbol the walk did not follow: the depth limit, or a caller followed upstream only.",
            "stopped" to "Links to symbols not reached: the walk stopped at the node limit or the read limit.",
        ),
    )

    fun of(serialName: String, field: String): String? = byType[serialName]?.get(field)
}
