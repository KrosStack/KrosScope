package io.github.krosstack.kscope.cli.text.format

import com.github.ajalt.mordant.rendering.AnsiLevel
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeOnTerminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmphasisTest {
    private val escape = 27.toChar()

    @Test
    fun `text on a terminal that shows colour is emphasised`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val result = runKscopeOnTerminal("info", "--workspace", root.toString())

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("$escape[1m"), "no bold in: ${result.stdout}")
    }

    @Test
    fun `captured text never holds an escape code`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val captured = runKscope("info", "--workspace", root.toString())

        assertFalse(captured.stdout.contains(escape), captured.stdout)
        assertFalse(captured.stderr.contains(escape), captured.stderr)
    }

    @Test
    fun `NO_COLOR keeps a terminal plain`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val result = runKscopeOnTerminal("info", "--workspace", root.toString(), variables = mapOf("NO_COLOR" to "1"))

        assertFalse(result.stdout.contains(escape), result.stdout)
    }

    @Test
    fun `a terminal without colour is not given escape codes`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val result = runKscopeOnTerminal("info", "--workspace", root.toString(), ansiLevel = AnsiLevel.NONE)

        assertFalse(result.stdout.contains(escape), result.stdout)
    }

    @Test
    fun `json and markdown are never emphasised`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        listOf("json", "markdown").forEach { format ->
            val result = runKscopeOnTerminal("info", "--workspace", root.toString(), "--format", format)

            assertFalse(result.stdout.contains(escape), "$format: ${result.stdout}")
        }
    }
}
