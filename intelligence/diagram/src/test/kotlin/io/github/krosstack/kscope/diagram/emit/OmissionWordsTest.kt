package io.github.krosstack.kscope.diagram.emit

import io.github.krosstack.kscope.diagram.Diagram
import io.github.krosstack.kscope.diagram.DiagramKind
import io.github.krosstack.kscope.diagram.DrawnState
import io.github.krosstack.kscope.diagram.Omission
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class OmissionWordsTest {
    private val prefix = "Omitted links: exact=0, probable=0, heuristic=0, unresolved=0, not-analysed=0"

    private val everyReason = Omission(
        0,
        emptyMap(),
        notExpanded = 1,
        membersNotRead = 1,
        callsNotDrawn = mapOf("kotlin.class" to 2, "kotlin.property" to 1),
        notInType = 1,
        notCallable = 1,
        withoutSymbol = 1,
        withinType = 1,
        beyondDepth = 1,
        stopped = 1,
    )

    private fun diagram(omitted: Omission): Diagram = Diagram(
        DiagramKind.SYMBOL,
        "Test",
        emptyList(),
        emptyList(),
        emptyList(),
        emptyList(),
        omitted.copy(linksByState = DrawnState.entries.associateWith { 0 }),
        emptyList(),
    )

    private fun footer(omitted: Omission): String = diagram(omitted).omissionText()

    @Test
    fun `a diagram without omissions prints the link counts only`() {
        assertEquals(prefix, footer(Omission(0, emptyMap())))
    }

    @Test
    fun `a reason is printed as its count and a line that says what it counts and why`() {
        val text = footer(Omission(0, emptyMap(), stopped = 12))
        assertEquals(
            "$prefix; 12 links to symbols not reached: the walk stopped at the node limit or the read limit",
            text,
        )
    }

    @Test
    fun `every reason has one line in words and none prints its field name`() {
        val text = footer(everyReason)
        val lines = listOf(
            "1 symbols not expanded: the walk stopped before it read their links",
            "1 members not read: the read limit left their links unread",
            "1 links with an end in no type: a top-level function, a file facade or a class the index lacks",
            "1 links with a caller or target that is no callable: the call level draws callables only",
            "1 links with an end that has no symbol: an external library or an id the index lacks",
            "1 links between two members of one type: a type has no edge to itself",
            "1 links from a symbol the walk did not follow: the depth limit, or a caller followed upstream only",
            "1 links to symbols not reached: the walk stopped at the node limit or the read limit",
            "calls to targets that are no callable, not drawn, by target kind: kotlin.class=2, kotlin.property=1",
        )
        assertEquals(prefix + lines.joinToString("") { "; $it" }, text)
        listOf("notExpanded", "membersNotRead", "callsNotDrawn", "notInType", "notCallable", "withoutSymbol").forEach {
            assertFalse(it in text, it)
        }
        listOf("withinType", "beyondDepth").forEach { assertFalse(it in text, it) }
    }

    @Test
    fun `a count of a thousand or more has a comma whatever the default locale is`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertTrue("; 16,630 links to symbols" in footer(Omission(0, emptyMap(), stopped = 16_630)))
            assertTrue("; 1,234,567 symbols not expanded" in footer(Omission(0, emptyMap(), notExpanded = 1_234_567)))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `the words need no escape in mermaid and dot`() {
        val text = footer(everyReason)
        assertEquals(text.mermaidEscape(), text)
        assertEquals(text.dotEscape(), text)
    }

    @Test
    fun `both text outputs end the footer with the same words`() {
        val diagram = diagram(Omission(0, emptyMap(), beyondDepth = 3))
        val words = "; 3 links from a symbol the walk did not follow: " +
            "the depth limit, or a caller followed upstream only"
        assertTrue(MermaidEmitter.render(diagram).contains("$words\"]:::metadata"))
        assertTrue(DotEmitter.render(diagram).contains("$words\", shape=note"))
    }
}
