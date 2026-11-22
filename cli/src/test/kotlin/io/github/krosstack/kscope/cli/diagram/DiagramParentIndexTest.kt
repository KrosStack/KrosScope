package io.github.krosstack.kscope.cli.diagram

import io.github.krosstack.kscope.cli.lookup.LIB_FILE
import io.github.krosstack.kscope.cli.lookup.NO_INDEX_LINE
import io.github.krosstack.kscope.cli.lookup.NestedWorkspace
import io.github.krosstack.kscope.cli.lookup.inNestedWorkspace
import io.github.krosstack.kscope.cli.lookup.runIn
import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramParentIndexTest {
    private val queries = listOf(
        listOf("diagram", "modules"),
        listOf("diagram", "components"),
        listOf("diagram", "symbol", "Lib"),
    )
    private val leading = listOf("schemaVersion", "indexLevel", "indexLocation")

    private fun assertNamesParentIndex(nested: NestedWorkspace, found: String, stated: String, query: List<String>) {
        val json = parseJson(found)
        assertEquals(nested.index, json.getValue("indexLocation").jsonPrimitive.content, query.toString())
        assertEquals(leading, json.keys.take(leading.size), query.toString())
        assertEquals(parseJson(stated), JsonObject(json - "indexLocation"), query.toString())
    }

    @Test
    fun `a diagram asked from a subfolder answers from the index of a parent folder`() = inNestedWorkspace { nested ->
        queries.forEach { query ->
            listOf("mermaid", "dot", "json").forEach { format ->
                val arguments = query + listOf("--format", format, "--quiet")
                val found = nested.run(arguments)
                val stated = nested.stated(arguments)

                assertEquals(0, found.statusCode, "$arguments: ${found.output}")
                if (format == "json") {
                    assertNamesParentIndex(nested, found.stdout, stated.stdout, arguments)
                } else {
                    assertEquals(stated.stdout, found.stdout, arguments.toString())
                }
                val note = if (format == "json") "" else nested.line + "\n"
                assertEquals(note + stated.stderr, found.stderr, arguments.toString())
            }
        }
    }

    @Test
    fun `a diagram without a format is drawn in mermaid from a subfolder`() = inNestedWorkspace { nested ->
        val found = nested.run(listOf("diagram", "modules", "--quiet"))
        val named = nested.run(listOf("diagram", "modules", "--format", "mermaid", "--quiet"))

        assertEquals(0, found.statusCode, found.output)
        assertEquals(named.stdout, found.stdout)
    }

    @Test
    fun `a location typed from a subfolder names the file of its workspace path`() = inNestedWorkspace { nested ->
        val found = nested.run(listOf("diagram", "symbol", "Lib.kt:1", "--format", "json", "--quiet"))
        val stated = nested.stated(listOf("diagram", "symbol", "$LIB_FILE:1", "--format", "json", "--quiet"))

        assertEquals(0, found.statusCode, found.output)
        assertNamesParentIndex(nested, found.stdout, stated.stdout, listOf("diagram", "symbol", "Lib.kt:1"))
    }

    @Test
    fun `an unknown name asked from a subfolder names the index of the parent folder in json`() =
        inNestedWorkspace { nested ->
            val query = listOf("diagram", "symbol", "Absent", "--format", "json", "--quiet")
            val found = nested.run(query)
            val stated = nested.stated(query)

            assertEquals(0, found.statusCode, found.output)
            assertNamesParentIndex(nested, found.stdout, stated.stdout, query)
        }

    @Test
    fun `a folder with no index above it says that no index was found`() = inNestedWorkspace { nested ->
        val result = runIn(nested.base, nested.base, listOf("diagram", "modules", "--quiet"))

        assertTrue(result.stderr.startsWith(NO_INDEX_LINE), result.stderr)
        assertEquals("", result.stdout)
    }
}
