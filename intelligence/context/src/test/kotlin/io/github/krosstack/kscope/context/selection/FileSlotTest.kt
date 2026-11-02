package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.context.ContextFragment
import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class FileSlotTest {
    private val artifact = artifactNamed("file")
    private val slot = FileSlot(artifact, 3, 7)
    private val first = signature("first", 0.3)
    private val second = signature("second", 0.2)
    private val third = signature("third", 0.1)

    @Test
    fun `the entries with a fragment keep the order in which their nodes were first accepted`() {
        acceptAll(first, second, third)
        val fourth = signature("fourth", 0.4)

        assertEquals(listOf(first, second, third, fourth), slot.entriesWith(fourth.node, fourth))
    }

    @Test
    fun `a node that is already present is replaced where it stood`() {
        acceptAll(first, second, third)
        val upgraded = second.copy(rendering = ContextRendering.DECLARATION, relevance = 0.5)

        assertEquals(listOf(first, upgraded, third), slot.entriesWith(second.node, upgraded))

        slot.accept(second.node, upgraded, renderedFile(0.5))
        assertEquals(listOf(first, upgraded, third), slot.entriesWith(third.node, third))
    }

    @Test
    fun `asking for the entries with a fragment does not change the slot`() {
        acceptAll(first, second)

        slot.entriesWith(third.node, third)

        assertEquals(listOf(first, second), slot.entriesWith(second.node, second))
        assertEquals(2, slot.version)
    }

    @Test
    fun `accepting makes the slot present with the new rendering and raises its version`() {
        val rendered = renderedFile(0.3)

        assertFalse(slot.isPresent)
        assertNull(slot.rendered)
        assertEquals(0, slot.version)

        slot.accept(first.node, first, rendered)

        assertTrue(slot.isPresent)
        assertSame(rendered, slot.rendered)
        assertEquals(1, slot.version)
        assertEquals(7, slot.unresolvedCount)
    }

    private fun signature(name: String, relevance: Double) =
        fragmentOf(artifact, ContextRendering.SIGNATURE, relevance, NodeRef.OfSymbol(SymbolId(name)))

    private fun acceptAll(vararg fragments: ContextFragment) {
        for (fragment in fragments) slot.accept(fragment.node, fragment, renderedFile(fragment.relevance))
    }
}
