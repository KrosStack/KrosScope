package io.github.krosstack.kscope.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressOptionsTest {
    private fun scan(recorded: RecordedEnvironment, root: Path, options: List<String> = emptyList()) =
        runKscopeWith(recorded.environment, listOf("scan", "--workspace", root.toString()) + options)

    @Test
    fun `captured progress is one plain line per stage on standard error and never on standard output`() =
        inWorkspace { root ->
            val recorded = RecordedEnvironment(stderrIsConsole = false)
            val result = scan(recorded, root)

            assertEquals(0, result.statusCode)
            val lines = recorded.written.lines().filter { it.isNotEmpty() }
            assertTrue(lines.size in 2..12, recorded.written)
            assertTrue(lines.first().startsWith("Listing files: "), recorded.written)
            assertTrue(lines.last().startsWith("Done in "), recorded.written)
            assertFalse('\r' in recorded.written, "plain progress holds no carriage return")
            assertEquals("", result.stderr)
            val standardOutputLines = result.stdout.lines().toSet()
            lines.forEach { assertFalse(it in standardOutputLines, "progress line on standard output: $it") }
        }

    @Test
    fun `on a terminal progress is one live line that is cleared at the end`() = inWorkspace { root ->
        val recorded = RecordedEnvironment(stderrIsConsole = true)
        scan(recorded, root)

        assertTrue(recorded.written.startsWith("\r"), recorded.written)
        assertTrue(recorded.written.contains("Listing files"), recorded.written)
        assertTrue(Regex("""\r +\rDone in [^\r\n]+\.\n$""").containsMatchIn(recorded.written), recorded.written)
        assertEquals(1, recorded.written.count { it == '\n' }, "only the summary ends a line")
    }

    @Test
    fun `progress never and quiet show nothing`() = inWorkspace { root ->
        listOf(
            listOf("--progress", "never"),
            listOf("--quiet"),
            listOf("-q"),
            listOf("--quiet", "--progress", "always"),
        ).forEach { options ->
            val recorded = RecordedEnvironment(stderrIsConsole = true)
            val result = scan(recorded, root, options)

            assertEquals(0, result.statusCode)
            assertEquals("", recorded.written, "options $options")
        }
    }

    @Test
    fun `progress always forces the live line when the stream is not a terminal`() = inWorkspace { root ->
        val recorded = RecordedEnvironment(stderrIsConsole = false)
        runKscope(recorded.environment, "--progress", "always", "scan", "--workspace", root.toString())

        assertTrue(recorded.written.startsWith("\r"), recorded.written)
    }

    @Test
    fun `queries report the index load and the ranking`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val recorded = RecordedEnvironment(stderrIsConsole = false)
        val result = runKscope(recorded.environment, "analyze", "Lib", "--as", "task", "--workspace", root.toString())

        assertEquals(0, result.statusCode, result.output)
        assertTrue(recorded.written.contains("Loading the index ("), recorded.written)
        assertTrue(recorded.written.contains("Ranking files and symbols ("), recorded.written)
    }

    @Test
    fun `quiet progress leaves the json result byte for byte as it was`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val arguments = listOf("analyze", "Lib", "--workspace", root.toString(), "--format", "json")
        val loud = runKscopeWith(arguments)
        val recorded = RecordedEnvironment(stderrIsConsole = true)
        val observed = runKscopeWith(recorded.environment, arguments)
        val quiet = runKscopeWith(recorded.environment, listOf("--quiet") + arguments)

        assertEquals(loud.stdout, observed.stdout)
        assertEquals(loud.stdout, quiet.stdout)
        assertTrue(recorded.written.isNotEmpty(), "the observed run did report progress")
    }

    @Test
    fun `errors still reach standard error when progress is off`() = inWorkspace { root ->
        writeFile(root, ".kscope/index.db", "this is not a database")
        val recorded = RecordedEnvironment(stderrIsConsole = true)
        val result = scan(recorded, root, listOf("--quiet"))

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.startsWith("error: "), result.stderr)
        assertEquals("", recorded.written)
    }

    @Test
    fun `progress options are accepted before and after the command name`() = inScannedWorkspace { root ->
        val before = runKscope("--quiet", "--progress", "never", "info", "--workspace", root.toString())
        val after = runKscope("info", "--workspace", root.toString(), "--quiet", "--progress", "never")

        assertEquals(0, before.statusCode)
        assertEquals(0, after.statusCode)
        assertEquals(2, runKscope("info", "--workspace", root.toString(), "--progress", "sometimes").statusCode)
    }
}
