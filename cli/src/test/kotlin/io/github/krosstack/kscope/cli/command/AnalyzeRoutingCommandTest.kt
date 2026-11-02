package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.inGreeterWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.printedAt
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.writeFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalyzeRoutingCommandTest {
    private fun scanned(block: (Path) -> Unit) = inGreeterWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        block(root)
    }

    @Test
    fun `a name that nothing declares falls back to a task and says so`() = scanned { root ->
        val text = printedAt(root, "analyze", "Frobnicator")

        assertEquals(
            "Nothing is named \"Frobnicator\", so it was read as a task (use --as symbol to look for a name only).",
            text.lines().first(),
        )
        assertTrue(text.contains("Nothing in the index matches this task."), text)
    }

    @Test
    fun `as symbol on a missing name says so and keeps the task reading one flag away`() = scanned { root ->
        val text = printedAt(root, "analyze", "greet a person by name", "--as", "symbol")

        Golden.assertMatches("analyze-card-missing", text)
    }

    @Test
    fun `as task reads a name as a task`() = scanned { root ->
        val text = printedAt(root, "analyze", "Greeter", "--as", "task")

        assertEquals("Read as a task (use --as symbol to look it up as a name).", text.lines().first())
        assertTrue(text.contains("Files to edit"), text)
    }

    @Test
    fun `json is the task analysis whatever the argument looks like`() = scanned { root ->
        val guessed = printedAt(root, "analyze", "Greeter", "--format", "json")
        val asTask = printedAt(root, "analyze", "Greeter", "--as", "task", "--format", "json")
        val asSymbol = runKscopeWith(
            listOf("analyze", "Greeter", "--as", "symbol", "--format", "json", "--workspace", root.toString()),
        )

        assertEquals(guessed, asTask)
        assertTrue(parseJson(guessed).containsKey("files"))
        assertEquals(2, asSymbol.statusCode, asSymbol.output)
    }

    @Test
    fun `images are counted and documents are listed apart in a task analysis`() = scanned { root ->
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13)
        Files.write(root.resolve("lib/src/main/greet.png"), signature)
        writeFile(root, "docs/Greet.md", "# Greet a person by name\n\nThe greeter greets a person.\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        Golden.assertMatches("analyze-grouped", printedAt(root, "analyze", "greet a person by name"))
    }
}
