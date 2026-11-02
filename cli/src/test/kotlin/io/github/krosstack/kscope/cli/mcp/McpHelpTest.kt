package io.github.krosstack.kscope.cli.mcp

import io.github.krosstack.kscope.cli.runKscope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpHelpTest {
    @Test
    fun `mcp shares the serve group and ends its help with examples`() {
        val help = runKscope("--help").stdout
        val group = help.substringAfter("Keep it ready:").substringBefore("Diagnose:")
        assertTrue(Regex("(?m)^ {2}mcp +\\S").containsMatchIn(group), group)
        val result = runKscope("mcp", "--help")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.trimEnd().substringAfterLast("\n\n").startsWith("Examples:"))
    }
}
