package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedDeepWorkspace
import io.github.krosstack.kscope.cli.printedAt
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.text.AnalyzeText
import io.github.krosstack.kscope.cli.text.UnitCardText
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzeSectionsTest {
    private fun rejects(root: Path, arguments: List<String>, section: String, available: List<String>) {
        val result = runKscopeWith(
            listOf("analyze") + arguments + listOf("--sections", section, "--workspace", root.toString()),
        )

        assertEquals(2, result.statusCode, result.output)
        assertEquals("", result.stdout)
        assertTrue(result.stderr.contains("Unknown section $section"), result.stderr)
        assertTrue(result.stderr.contains("The sections here are: ${available.joinToString(", ")}."), result.stderr)
    }

    @Test
    fun `a sentence rejects a card section`() = inScannedDeepWorkspace { root ->
        rejects(root, listOf("add a feature"), "declaration", AnalyzeText.SECTIONS)
    }

    @Test
    fun `a name rejects a task section`() = inScannedDeepWorkspace { root ->
        rejects(root, listOf("Greeter"), "query", UnitCardText.SECTIONS)
    }

    @Test
    fun `a name forced to task rejects a card section`() = inScannedDeepWorkspace { root ->
        rejects(root, listOf("Greeter", "--as", "task"), "declaration", AnalyzeText.SECTIONS)
    }

    @Test
    fun `a sentence forced to symbol rejects a task section`() = inScannedDeepWorkspace { root ->
        rejects(root, listOf("add a feature", "--as", "symbol"), "query", UnitCardText.SECTIONS)
    }

    @Test
    fun `a guessed name that falls back to task uses task sections`() = inScannedDeepWorkspace { root ->
        rejects(root, listOf("Frobnicator"), "declaration", AnalyzeText.SECTIONS)
        val result = runKscopeWith(
            listOf("analyze", "Frobnicator", "--sections", "query", "--workspace", root.toString()),
        )
        assertEquals(0, result.statusCode, result.output)
    }

    @Test
    fun `valid task and card sections still print`() = inScannedDeepWorkspace { root ->
        val task = printedAt(root, "analyze", "greet a person", "--sections", "query")
        val card = printedAt(root, "analyze", "Greeter", "--sections", "declaration")

        assertTrue(task.contains("Task"), task)
        assertTrue(task.contains("greet a person"), task)
        assertTrue(card.contains("Declared at"), card)
        assertTrue(card.contains("lib/src/main/kotlin/Greeter.kt"), card)
    }
}
