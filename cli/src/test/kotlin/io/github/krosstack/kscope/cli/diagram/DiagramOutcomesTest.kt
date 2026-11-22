package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramOutcomesTest {
    @Test
    fun `a symbol that nothing declares is said so on standard output and ends with success like symbol`() {
        inDiagramWorkspace { root ->
            val text = runDiagram(root, "symbol", "Absent")
            val lookup = runKscope("symbol", "Absent", "--workspace", root.toString(), "--quiet")

            assertEquals(0, text.statusCode, text.output)
            assertEquals(lookup.statusCode, text.statusCode)
            assertEquals("No declaration matches \"Absent\".", text.stdout.trimEnd())
            assertEquals("", text.stderr)
        }
    }

    @Test
    fun `a module or repository that is not in the index is said so on standard output`() {
        inDiagramWorkspace { root ->
            val module = runDiagram(root, "modules", ":absent")
            val repository = runDiagram(root, "components", "absent")

            assertEquals(0, module.statusCode, module.output)
            assertEquals("No module matches \":absent\". Name a module by its path or id.", module.stdout.trimEnd())
            assertEquals(0, repository.statusCode, repository.output)
            assertEquals(
                "No repository matches \"absent\". Name a repository by its name or id.",
                repository.stdout.trimEnd(),
            )
            assertEquals("", module.stderr + repository.stderr)
        }
    }

    @Test
    fun `a name that several declarations share lists them and is a usage error`() {
        inDiagramWorkspace { root ->
            val text = runDiagram(root, "symbol", "Lib")
            val json = runDiagram(root, "symbol", "Lib", "--format", "json")

            assertEquals(ExitCodes.USAGE, text.statusCode, text.output)
            assertTrue(text.stdout.contains("\"Lib\" is ambiguous: 2 declarations match."), text.stdout)
            assertTrue(text.stdout.contains("other.Lib"), text.stdout)
            assertEquals(ExitCodes.USAGE, json.statusCode, json.output)
            assertEquals(2, parseJson(json.stdout).getValue("candidates").jsonArray.size)
            assertEquals("", text.stderr + json.stderr)
        }
    }

    @Test
    fun `a symbol that is neither a type nor a function is not drawn`() {
        inDiagramWorkspace { root ->
            val result = runDiagram(root, "symbol", "total")

            assertEquals(ExitCodes.USAGE, result.statusCode, result.output)
            assertEquals("", result.stdout)
            assertTrue(result.stderr.contains("total"), result.stderr)
            assertTrue(result.stderr.contains("Name a type or a function."), result.stderr)
        }
    }

    @Test
    fun `a budget below the smallest diagram says how many characters are needed`() {
        inDiagramWorkspace { root ->
            val result = runDiagram(root, "symbol", "Greeter", "--characters", "10")

            assertEquals(ExitCodes.USAGE, result.statusCode, result.output)
            assertEquals("", result.stdout)
            val message = Regex("--characters 10 is too small; .* at least \\d+ characters")
            assertTrue(message.containsMatchIn(result.stderr), result.stderr)
        }
    }

    @Test
    fun `the symbol kind needs a node`() {
        inDiagramWorkspace { root ->
            val result = runDiagram(root, "symbol")

            assertEquals(ExitCodes.USAGE, result.statusCode, result.output)
            assertTrue(result.stderr.contains("diagram symbol needs a node"), result.stderr)
        }
    }

    @Test
    fun `values that no diagram can use are usage errors`() {
        inDiagramWorkspace { root ->
            val invalid = listOf(
                listOf("unknown"),
                listOf("modules", "--depth", "-1"),
                listOf("modules", "--links", "-1"),
                listOf("modules", "--characters", "0"),
                listOf("modules", "--format", "text"),
                listOf("modules", "--format", "markdown"),
            )
            invalid.forEach { arguments ->
                val result = runDiagram(root, arguments)
                assertEquals(ExitCodes.USAGE, result.statusCode, "$arguments: ${result.output}")
                assertEquals("", result.stdout, arguments.toString())
            }
        }
    }

    @Test
    fun `mermaid is the format of the diagram command only`() {
        inDiagramWorkspace { root ->
            val others = listOf(listOf("graph"), listOf("info"), listOf("symbol", "Greeter"))
            others.forEach { arguments ->
                val result = runKscopeWith(arguments + listOf("--format", "mermaid", "--workspace", root.toString()))
                assertEquals(ExitCodes.USAGE, result.statusCode, "$arguments: ${result.output}")
                assertTrue(result.stderr.contains("--format mermaid is available for the diagram command only."))
            }
            val dot = runKscope("info", "--format", "dot", "--workspace", root.toString())
            assertTrue(dot.stderr.contains("--format dot is available for the graph and diagram commands only."))
        }
    }
}
