package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.Golden
import io.github.krosstack.kscope.cli.inGreeterWorkspace
import io.github.krosstack.kscope.cli.printedAt
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class UnitCardCommandTest {
    private fun scanned(block: (Path) -> Unit) = inGreeterWorkspace { root ->
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        block(root)
    }

    @Test
    fun `a name prints a card with the declaration its links its tests and its impact`() = scanned { root ->
        val card = printedAt(root, "analyze", "Greeter")

        Golden.assertMatches("analyze-card-symbol", card)
        assertEquals("Read \"Greeter\" as a name (use --as task to analyse it as a task).", card.lines().first())
        assertEquals(card.replace("\"Greeter\"", "\"greet.Greeter\""), printedAt(root, "analyze", "greet.Greeter"))
    }

    @Test
    fun `a file prints a card with what it declares and where it sits`() = scanned { root ->
        Golden.assertMatches("analyze-card-file", printedAt(root, "analyze", "lib/src/main/kotlin/Greeter.kt"))
    }

    @Test
    fun `a name that several declarations share prints a card and lists the alternatives`() = scanned { root ->
        writeFile(root, "app/src/main/kotlin/Other.kt", "package other\n\nclass Greeter\n")
        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)

        Golden.assertMatches("analyze-card-ambiguous", printedAt(root, "analyze", "Greeter"))
    }

    @Test
    fun `a brief card keeps the declaration the test count and the next step`() = scanned { root ->
        Golden.assertMatches("analyze-card-brief", printedAt(root, "analyze", "Greeter", "--detail", "brief"))
    }

    @Test
    fun `named sections of a card print alone`() = scanned { root ->
        val card = printedAt(root, "analyze", "Greeter", "--sections", "tests,impact")

        Golden.assertMatches("analyze-card-sections", card)
    }

    @Test
    fun `markdown prints the same card as text`() = scanned { root ->
        val text = printedAt(root, "analyze", "Greeter")

        assertEquals(text, printedAt(root, "analyze", "Greeter", "--format", "markdown"))
    }
}
