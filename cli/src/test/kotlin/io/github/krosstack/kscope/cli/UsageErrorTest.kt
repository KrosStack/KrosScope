package io.github.krosstack.kscope.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageErrorTest {
    @Test
    fun `a missing workspace is a usage error`() {
        val result = runKscope("scan", "--workspace", "this-directory-does-not-exist")
        assertEquals(2, result.statusCode)
        assertTrue(result.stderr.contains("--workspace"))
    }

    @Test
    fun `an unknown format is a usage error`() = inWorkspace { root ->
        assertEquals(2, runKscope("info", "--workspace", root.toString(), "--format", "xml").statusCode)
    }

    @Test
    fun `an unknown command is a usage error`() {
        assertEquals(2, runKscope("frobnicate").statusCode)
    }

    @Test
    fun `running without arguments prints the help`() {
        val result = runKscope()
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains("scan"))
        assertTrue(result.output.contains("info"))
    }
}
