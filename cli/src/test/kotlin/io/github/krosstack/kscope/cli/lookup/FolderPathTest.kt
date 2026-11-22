package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class FolderPathTest {
    private fun pairs(typed: String, named: String) = listOf(
        listOf("impact", typed) to listOf("impact", named),
        listOf("symbol", "$typed:1") to listOf("symbol", "$named:1"),
        listOf("related", typed) to listOf("related", named),
        listOf("graph", "--scope", "neighborhood", "--node", typed) to
            listOf("graph", "--scope", "neighborhood", "--node", named),
        listOf("analyze", typed) to listOf("analyze", named),
    )

    @Test
    fun `a file typed from the current folder is the file of its workspace path`() = inNestedWorkspace { nested ->
        pairs("Lib.kt", LIB_FILE).forEach { (typed, named) ->
            val text = nested.run(typed + "--quiet")
            val json = nested.run(typed + listOf("--format", "json", "--quiet"))

            assertEquals(0, text.statusCode, "$typed: ${text.output}")
            assertEquals(nested.stated(named + "--quiet").stdout, text.stdout, typed.toString())
            assertEquals(
                parseJson(nested.stated(named + listOf("--format", "json", "--quiet")).stdout),
                JsonObject(parseJson(json.stdout) - "indexLocation"),
                typed.toString(),
            )
        }
    }

    @Test
    fun `a workspace path still names its file from a subfolder`() = inNestedWorkspace { nested ->
        pairs(LIB_FILE, LIB_FILE).forEach { (typed, named) ->
            val result = nested.run(typed + "--quiet")

            assertEquals(0, result.statusCode, "$typed: ${result.output}")
            assertEquals(nested.stated(named + "--quiet").stdout, result.stdout, typed.toString())
        }
    }

    @Test
    fun `a file of the current folder wins over a file of that name in the workspace root`() =
        inNestedWorkspace { nested ->
            val library = runIn(nested.root.resolve("lib"), nested.base, listOf("impact", "README.md", "--quiet"))

            assertEquals(0, library.statusCode, library.output)
            assertEquals(nested.stated(listOf("impact", "lib/README.md", "--quiet")).stdout, library.stdout)
        }
}
