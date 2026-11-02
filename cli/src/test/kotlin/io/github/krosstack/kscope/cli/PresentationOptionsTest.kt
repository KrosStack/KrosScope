package io.github.krosstack.kscope.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PresentationOptionsTest {
    private fun run(root: Path, vararg arguments: String) =
        runKscopeWith(arguments.toList() + listOf("--workspace", root.toString()))

    @Test
    fun `detail and sections are refused by a command that cannot honour them`() = inScannedDeepWorkspace { root ->
        listOf("info", "scan", "doctor", "graph", "impact Greeter", "context task").forEach {
            val result = runKscopeWith(it.split(" ") + listOf("--detail", "brief", "--workspace", root.toString()))
            assertEquals(2, result.statusCode, "$it: ${result.output}")
            assertTrue(result.stderr.contains("available for analyze, symbol and related only"), result.stderr)
        }
        assertEquals(2, run(root, "info", "--sections", "tests").statusCode)
    }

    @Test
    fun `detail and sections apply to text and markdown but not to json`() = inScannedDeepWorkspace { root ->
        listOf("--detail" to "brief", "--sections" to "tests").forEach { (option, value) ->
            val result = run(root, "analyze", "Greeter", option, value, "--format", "json")
            assertEquals(2, result.statusCode, result.output)
            assertTrue(result.stderr.contains("JSON always has the full result"), result.stderr)
        }
        assertEquals(0, run(root, "analyze", "Greeter", "--detail", "full", "--format", "markdown").statusCode)
    }

    @Test
    fun `an unknown or empty section is a usage error that names the sections`() = inScannedDeepWorkspace { root ->
        val unknown = run(root, "symbol", "Greeter", "--sections", "headline,bogus")
        val empty = run(root, "related", "Greeter", "--sections", "")

        assertEquals(2, unknown.statusCode, unknown.output)
        assertTrue(unknown.stderr.contains("Unknown section bogus"), unknown.stderr)
        assertTrue(unknown.stderr.contains("headline, best, matches"), unknown.stderr)
        assertEquals(2, empty.statusCode, empty.output)
    }

    @Test
    fun `detail can come before the command name`() = inScannedDeepWorkspace { root ->
        val before = runKscope("--detail", "brief", "symbol", "Greeter", "--workspace", root.toString())
        val after = run(root, "symbol", "Greeter", "--detail", "brief")

        assertEquals(0, before.statusCode, before.output)
        assertEquals(after.stdout, before.stdout)
        assertFalse(after.stdout.contains("Declared at"), after.stdout)
    }

    @Test
    fun `symbol and related honour detail and sections`() = inScannedDeepWorkspace { root ->
        val brief = printedAt(root, "related", "lib/src/main/kotlin/Greeter.kt", "--detail", "brief")
        val matches = printedAt(root, "symbol", "Greeter", "--sections", "matches")

        Golden.assertAllMatch(mapOf("related-brief" to brief, "symbol-sections" to matches))
    }

    @Test
    fun `the options and the section names are documented in the help`() {
        val space = Regex("\\s+")
        val analyze = runKscope("analyze", "--help").stdout.replace(space, " ")
        val symbol = runKscope("symbol", "--help").stdout.replace(space, " ")
        val related = runKscope("related", "--help").stdout.replace(space, " ")

        listOf("--as", "--detail", "--sections").forEach { assertTrue(analyze.contains(it), analyze) }
        assertTrue(analyze.contains("headline, top, query, edit, read"), analyze)
        assertTrue(analyze.contains("headline, declaration, depends-on, referred-by"), analyze)
        assertTrue(symbol.contains("headline, best, matches"), symbol)
        assertTrue(related.contains("headline, files, symbols, support"), related)
    }
}
