package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramNotFoundTest {
    @Test
    fun `an unknown symbol with json is a node resolution without candidates and ends with success`() {
        inDiagramWorkspace { root ->
            assertNothingMatches(root, listOf("symbol", "Absent"), "Absent")
        }
    }

    @Test
    fun `an unknown module with json is a node resolution without candidates and ends with success`() {
        inDiagramWorkspace { root ->
            assertNothingMatches(root, listOf("modules", ":absent"), ":absent")
        }
    }

    @Test
    fun `an unknown repository with json is a node resolution without candidates and ends with success`() {
        inDiagramWorkspace { root ->
            assertNothingMatches(root, listOf("components", "absent"), "absent")
        }
    }

    @Test
    fun `the other formats keep the plain message for an unknown name`() {
        inDiagramWorkspace { root ->
            listOf("mermaid", "dot").forEach { format ->
                val result = runDiagram(root, "symbol", "Absent", "--format", format)

                assertEquals(0, result.statusCode, result.output)
                assertEquals("No declaration matches \"Absent\".", result.stdout.trimEnd(), format)
                assertEquals("", result.stderr, format)
            }
        }
    }

    private fun assertNothingMatches(root: Path, arguments: List<String>, requested: String) {
        val result = runDiagram(root, arguments + listOf("--format", "json"))
        val json = parseJson(result.stdout)

        assertEquals(0, result.statusCode, result.output)
        assertEquals("", result.stderr)
        assertEquals(requested, json.getValue("requested").jsonPrimitive.content)
        assertTrue(json.getValue("candidates").jsonArray.isEmpty(), result.stdout)
        assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertTrue("indexLevel" in json, result.stdout)
    }
}
