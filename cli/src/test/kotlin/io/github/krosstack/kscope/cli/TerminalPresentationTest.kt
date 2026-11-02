package io.github.krosstack.kscope.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalPresentationTest {
    private val escape = 27.toChar()
    private val task = "greet a person by name"

    @Test
    fun `on a terminal the table header is emphasised and long paths fit the width`() = inScannedDeepWorkspace { root ->
        val result = runKscopeOnTerminal("analyze", task, "--workspace", root.toString(), width = WIDTH)
        val tooWide = result.stdout.lines().filter { it.contains("/") && it.length > WIDTH && !it.contains(escape) }

        assertTrue(Regex("$escape\\[[0-9;]*m  score").containsMatchIn(result.stdout), result.stdout)
        assertEquals(emptyList(), tooWide)
    }

    @Test
    fun `NO_COLOR keeps a terminal plain but still fits the width`() = inScannedDeepWorkspace { root ->
        val result = runKscopeOnTerminal(
            "analyze",
            task,
            "--workspace",
            root.toString(),
            width = WIDTH,
            variables = mapOf("NO_COLOR" to "1"),
        )

        assertFalse(result.stdout.contains(escape), result.stdout)
        assertEquals(emptyList(), result.stdout.lines().filter { it.contains("/") && it.length > WIDTH })
        assertTrue(result.stdout.contains("/.../"), result.stdout)
    }

    @Test
    fun `captured output keeps every path whole and plain`() = inScannedDeepWorkspace { root ->
        val text = printedAt(root, "analyze", "GreeterHelperFactory", "--as", "task")

        assertTrue(text.contains(DEEP_PATH), text)
        assertFalse(text.contains("/.../"), text)
        assertFalse(text.contains(escape), text)
    }

    @Test
    fun `markdown output is never emphasised or shortened even on a terminal`() = inScannedDeepWorkspace { root ->
        val result = runKscopeOnTerminal(
            "analyze",
            "GreeterHelperFactory",
            "--as",
            "task",
            "--format",
            "markdown",
            "--workspace",
            root.toString(),
            width = NARROW,
        )

        assertFalse(result.stdout.contains(escape), result.stdout)
        assertFalse(result.stdout.contains("/.../"), result.stdout)
    }

    @Test
    fun `symbol paths are shortened on a terminal and whole when captured`() = inScannedDeepWorkspace { root ->
        val onTerminal = runKscopeOnTerminal(
            "symbol",
            "GreeterHelperFactory",
            "--workspace",
            root.toString(),
            width = ROOMY,
        )

        assertTrue(onTerminal.stdout.contains("/.../GreeterHelperFactory.kt:3"), onTerminal.stdout)
        assertTrue(printedAt(root, "symbol", "GreeterHelperFactory").contains("directories/GreeterHelperFactory.kt:3"))
    }

    private companion object {
        const val WIDTH = 80
        const val NARROW = 50
        const val ROOMY = 110
    }
}
