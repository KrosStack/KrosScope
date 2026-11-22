package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramEdge
import io.github.krosstack.kscope.diagram.DrawnState
import java.util.Locale

private val inheritance = setOf("extends", "implements")

internal fun DiagramEdge.inherits(): Boolean = label.substringBefore(';') in inheritance

internal fun DiagramEdge.drawnLabel(): String {
    val number = position?.let { "$it: " }.orEmpty()
    val unknown = if (weakest >= DrawnState.UNRESOLVED) "? " else ""
    return number + unknown + label
}

internal fun Diagram.omissionText(): String {
    val links = omitted.linksByState.entries.joinToString(", ") {
        "${it.key.name.lowercase().replace('_', '-')}=${it.value}"
    }
    val reasons = OmissionReason.entries.filter { it.count(omitted) > 0 }
        .joinToString("") { "; ${it.count(omitted).grouped()} ${it.words}" }
    return "Omitted links: $links$reasons${callsNotDrawnText()}"
}

private fun Int.grouped(): String = String.format(Locale.ROOT, "%,d", this)

private fun Diagram.callsNotDrawnText(): String {
    val calls = omitted.callsNotDrawn.entries.joinToString(", ") { "${it.key}=${it.value}" }
    return if (calls.isEmpty()) "" else "; calls to targets that are no callable, not drawn, by target kind: $calls"
}
