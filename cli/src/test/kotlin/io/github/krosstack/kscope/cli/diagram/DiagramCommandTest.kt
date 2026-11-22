package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DiagramCommandTest {
    @Test
    fun `the modules diagram names the modules and is Mermaid unless asked otherwise`() {
        inDiagramWorkspace { root ->
            val result = runDiagram(root, "modules")

            assertEquals(0, result.statusCode, result.output)
            assertTrue(result.stdout.contains("flowchart LR"), result.stdout)
            assertTrue(result.stdout.contains(":app") && result.stdout.contains(":lib"), result.stdout)
            assertEquals(result.stdout, runDiagram(root, "modules", "--format", "mermaid").stdout)
            assertTrue(result.stdout.endsWith("\n") && !result.stdout.endsWith("\n\n"), "one closing newline")
        }
    }

    @Test
    fun `the components diagram draws the repositories`() {
        inDiagramWorkspace { root ->
            val json = parseJson(runDiagram(root, "components", "--format", "json").stdout)

            assertEquals("COMPONENTS", json.getValue("kind").jsonPrimitive.content)
            assertEquals("repository", diagramNodes(json).single().getValue("kind").jsonPrimitive.content)
        }
    }

    @Test
    fun `the symbol diagram draws a type and a function`() {
        inDiagramWorkspace { root ->
            val type = parseJson(runDiagram(root, "symbol", "Greeter", "--format", "json").stdout)
            val function = parseJson(runDiagram(root, "symbol", "salute", "--format", "json").stdout)

            assertEquals("SYMBOL", type.getValue("kind").jsonPrimitive.content)
            assertEquals("hello.Greeter", labelOf(selectedNode(type)))
            assertEquals("SYMBOL", function.getValue("kind").jsonPrimitive.content)
            assertTrue(labelOf(selectedNode(function)).contains("salute"), function.toString())
        }
    }

    @Test
    fun `each kind prints in each format`() {
        inDiagramWorkspace { root ->
            listOf(listOf("modules"), listOf("components"), listOf("symbol", "Greeter")).forEach { kind ->
                val mermaid = runDiagram(root, kind + listOf("--format", "mermaid"))
                val dot = runDiagram(root, kind + listOf("--format", "dot"))
                val json = runDiagram(root, kind + listOf("--format", "json"))

                assertTrue(mermaid.stdout.contains("flowchart LR"), "$kind: ${mermaid.stdout}")
                assertTrue(dot.stdout.startsWith("digraph diagram {"), "$kind: ${dot.stdout}")
                assertEquals(kind.first().uppercase(), parseJson(json.stdout).getValue("kind").jsonPrimitive.content)
                listOf(mermaid, dot, json).forEach { assertEquals(0, it.statusCode, "$kind: ${it.output}") }
            }
        }
    }

    @Test
    fun `json carries the schema version and the level of the index like the other commands`() {
        inDiagramWorkspace { root ->
            val result = runDiagram(root, "modules", "--format", "json")
            val json = parseJson(result.stdout)

            assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.int)
            assertEquals(listOf("schemaVersion", "indexLevel"), json.keys.take(2))
            assertEquals("", result.stderr)
            assertTrue(json.getValue("edges").jsonArray.isNotEmpty(), result.stdout)
        }
    }

    @Test
    fun `depth says how far a module diagram reaches from its node`() {
        inDiagramWorkspace { root ->
            val alone = runDiagram(root, "modules", ":lib", "--depth", "0", "--format", "json")
            val reached = runDiagram(root, "modules", ":lib", "--depth", "1", "--format", "json")

            val aloneNodes = diagramNodes(parseJson(alone.stdout))
            val reachedNodes = diagramNodes(parseJson(reached.stdout))

            assertEquals(1, aloneNodes.size)
            assertTrue(reachedNodes.size > aloneNodes.size, reached.stdout)
        }
    }

    @Test
    fun `links limits the edges that are drawn`() {
        inDiagramWorkspace { root ->
            val none = parseJson(runDiagram(root, "modules", "--links", "0", "--format", "json").stdout)

            assertTrue(none.getValue("edges").jsonArray.isEmpty(), none.toString())
            assertTrue(none.getValue("title").jsonPrimitive.content.contains("0 of"), none.toString())
        }
    }

    @Test
    fun `characters is the size of the Mermaid text that the diagram has to fit`() {
        inDiagramWorkspace { root ->
            val refused = runDiagram(root, "modules", "--characters", "10")
            val smallest = checkNotNull(Regex("at least (\\d+) characters").find(refused.stderr)).groupValues[1].toInt()

            val fits = runDiagram(root, "modules", "--characters", smallest.toString())
            val short = runDiagram(root, "modules", "--characters", (smallest - 1).toString())

            assertEquals(0, fits.statusCode, fits.output)
            assertEquals(2, short.statusCode, short.output)
            assertNotEquals(runDiagram(root, "modules").stdout, fits.stdout)
        }
    }
}
