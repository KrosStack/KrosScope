package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramNodeTest {
    @Test
    fun `a symbol is found by id, by qualified name, by simple name and by location`() {
        inDiagramWorkspace { root ->
            val ambiguous = parseJson(runDiagram(root, "symbol", "Lib", "--format", "json").stdout)
            val candidates = ambiguous.getValue("candidates").jsonArray.map { it.jsonPrimitive.content }
            val other = candidates.single { it.contains("other/Lib#") }

            val byId = runDiagram(root, "symbol", other, "--format", "json")
            val byQualifiedName = runDiagram(root, "symbol", "other.Lib", "--format", "json")
            val byLocation = runDiagram(root, "symbol", "lib/src/main/kotlin/Lib.kt:1", "--format", "json")
            val bySimpleName = runDiagram(root, "symbol", "Greeter", "--format", "json")

            listOf(byId, byQualifiedName, byLocation, bySimpleName).forEach {
                assertEquals(0, it.statusCode, it.output)
            }
            assertEquals(byId.stdout, byQualifiedName.stdout)
            assertEquals("other.Lib", labelOf(selectedNode(parseJson(byId.stdout))))
            assertEquals("Lib", labelOf(selectedNode(parseJson(byLocation.stdout))))
            assertEquals("hello.Greeter", labelOf(selectedNode(parseJson(bySimpleName.stdout))))
        }
    }

    @Test
    fun `a module is named by its path and a repository by its name`() {
        inDiagramWorkspace { root ->
            val byPath = runDiagram(root, "modules", ":app", "--format", "json")
            val everything = parseJson(runDiagram(root, "components", "--format", "json").stdout)
            val repository = labelOf(diagramNodes(everything).single())

            val byName = runDiagram(root, "components", repository, "--format", "json")

            assertEquals(0, byPath.statusCode, byPath.output)
            assertEquals(0, byName.statusCode, byName.output)
            assertTrue(diagramNodes(parseJson(byPath.stdout)).any { labelOf(it) == ":app" }, byPath.stdout)
        }
    }
}
