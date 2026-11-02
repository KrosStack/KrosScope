package io.github.krosstack.kscope.context.selection

import io.github.krosstack.kscope.context.fragment.ContextOffer
import io.github.krosstack.kscope.model.context.ContextRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class OfferCursorTest {
    private val artifact = artifactNamed("file")
    private val slot = FileSlot(artifact, 0, 0)
    private val signature = fragmentOf(artifact, ContextRendering.SIGNATURE, 0.5)
    private val declaration = fragmentOf(artifact, ContextRendering.DECLARATION, 0.5)

    @Test
    fun `a cursor offers its variants in order and has none left after the last`() {
        val cursor = OfferCursor(KeyedOffer(ContextOffer(listOf(signature, declaration)), "key"), slot)

        assertEquals("key", cursor.nodeKey)
        assertEquals(0, cursor.position)
        assertSame(signature, cursor.fragment)
        assertTrue(cursor.hasNext)

        cursor.advance()
        assertEquals(1, cursor.position)
        assertSame(declaration, cursor.fragment)
        assertTrue(cursor.hasNext)

        cursor.advance()
        assertNull(cursor.fragment)
        assertFalse(cursor.hasNext)
    }

    @Test
    fun `an offer of one variant has nothing left once it is accepted`() {
        val cursor = OfferCursor(KeyedOffer(ContextOffer(listOf(signature)), "key"), slot)

        cursor.advance()

        assertFalse(cursor.hasNext)
    }

    @Test
    fun `a remembered rendering is current until the slot of the cursor accepts a fragment`() {
        val cursor = OfferCursor(KeyedOffer(ContextOffer(listOf(signature, declaration)), "key"), slot)
        val rendered = renderedFile(1.0)
        val replacement = renderedFile(2.0)

        assertNull(cursor.currentRendering())
        assertSame(rendered, cursor.remember(rendered))
        assertSame(rendered, cursor.currentRendering())

        slot.accept(signature.node, signature, rendered)
        assertNull(cursor.currentRendering())

        assertSame(replacement, cursor.remember(replacement))
        assertSame(replacement, cursor.currentRendering())
    }

    @Test
    fun `cursors of different slots do not invalidate each other`() {
        val other = FileSlot(artifactNamed("other"), 1, 0)
        val cursor = OfferCursor(KeyedOffer(ContextOffer(listOf(signature)), "key"), slot)
        val rendered = cursor.remember(renderedFile(1.0))
        val fragment = fragmentOf(other.artifact, ContextRendering.SIGNATURE, 0.5)

        other.accept(fragment.node, fragment, renderedFile(3.0))

        assertSame(rendered, cursor.currentRendering())
    }
}
