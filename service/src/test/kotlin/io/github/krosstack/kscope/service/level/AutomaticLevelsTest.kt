package io.github.krosstack.kscope.service.level

import io.github.krosstack.kscope.service.ScanLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomaticLevelsTest {
    private fun next(indexed: ScanLevel?, file: ScanLevel?, trusted: Boolean = false) =
        AutomaticLevels.next(indexed, file, trusted)

    @Test
    fun `the first scan of a workspace is at syntax`() {
        assertEquals(ScanLevel.SYNTAX, next(null, null))
        assertEquals(ScanLevel.SYNTAX, next(null, ScanLevel.EXACT, trusted = true))
    }

    @Test
    fun `without stored trust a syntax index is deepened to resolved and never to exact`() {
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.SYNTAX, null))
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.SYNTAX, ScanLevel.EXACT))
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.RESOLVED, ScanLevel.EXACT))
        assertFalse(AutomaticLevels.isShallow(ScanLevel.RESOLVED, ScanLevel.EXACT, trusted = false))
        assertTrue(AutomaticLevels.isShallow(ScanLevel.SYNTAX, null, trusted = false))
    }

    @Test
    fun `with stored trust a file that asks for exact takes the index to exact`() {
        assertEquals(ScanLevel.EXACT, next(ScanLevel.SYNTAX, ScanLevel.EXACT, trusted = true))
        assertEquals(ScanLevel.EXACT, next(ScanLevel.RESOLVED, ScanLevel.EXACT, trusted = true))
        assertTrue(AutomaticLevels.isShallow(ScanLevel.RESOLVED, ScanLevel.EXACT, trusted = true))
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.SYNTAX, null, trusted = true))
        assertEquals(ScanLevel.EXACT, next(ScanLevel.EXACT, null, trusted = true))
    }

    @Test
    fun `revoked trust gives a level that the scan refuses, so the build never runs`() {
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.EXACT, ScanLevel.EXACT, trusted = false))
        assertTrue(AutomaticLevels.blocksUpdates(indexedExact = true, trusted = false))
        assertFalse(AutomaticLevels.blocksUpdates(indexedExact = true, trusted = true))
        assertFalse(AutomaticLevels.blocksUpdates(indexedExact = false, trusted = false))
    }

    @Test
    fun `a file that asks for syntax keeps the index at syntax and nothing becomes shallower`() {
        assertEquals(ScanLevel.SYNTAX, next(ScanLevel.SYNTAX, ScanLevel.SYNTAX))
        assertFalse(AutomaticLevels.isShallow(ScanLevel.SYNTAX, ScanLevel.SYNTAX, trusted = false))
        assertEquals(ScanLevel.RESOLVED, next(ScanLevel.RESOLVED, ScanLevel.SYNTAX))
        assertFalse(AutomaticLevels.isShallow(null, null, trusted = true))
    }
}
