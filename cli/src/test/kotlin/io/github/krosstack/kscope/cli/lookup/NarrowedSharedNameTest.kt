package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NarrowedSharedNameTest {
    private fun NestedWorkspace.application(arguments: List<String>) =
        runIn(root.resolve("application"), base, arguments + "--quiet")

    private fun candidates(json: String): List<String> =
        parseJson(json).getValue("candidates").jsonArray.map { it.jsonPrimitive.content }

    @Test
    fun `a simple name declared twice inside and once outside is ambiguous between the two inside`() =
        inSharedNameRepositories { workspace ->
            val text = workspace.application(listOf("symbol", "Twin"))
            val json = workspace.application(listOf("symbol", "Twin", "--format", "json"))
            val everywhere = workspace.stated(listOf("symbol", "Twin", "--format", "json", "--quiet"))
            val fields = parseJson(json.stdout)

            assertEquals(ExitCodes.USAGE, text.statusCode, text.output)
            assertEquals(ExitCodes.USAGE, json.statusCode, json.output)
            assertTrue(text.stdout.contains("2 declarations match"), text.stdout)
            assertTrue(text.stderr.startsWith(workspace.narrowedLine("application")), text.stderr)
            assertEquals("application", fields.getValue("narrowedTo").jsonPrimitive.content)
            assertEquals(2, candidates(json.stdout).size, json.stdout)
            assertEquals(3, candidates(everywhere.stdout).size, everywhere.stdout)
            assertTrue(candidates(everywhere.stdout).containsAll(candidates(json.stdout)))
            assertNull(fields["widenedFrom"])
        }

    @Test
    fun `a simple name declared once inside and once outside is the one inside`() =
        inSharedNameRepositories { workspace ->
            val text = workspace.application(listOf("symbol", "Shared"))
            val json = workspace.application(listOf("symbol", "Shared", "--format", "json"))
            val fields = parseJson(json.stdout)

            assertEquals(0, text.statusCode, text.output)
            assertEquals(0, json.statusCode, json.output)
            assertTrue(text.stdout.contains("application/"), text.stdout)
            assertFalse(text.stdout.contains("library/"), text.stdout)
            assertTrue(text.stderr.startsWith(workspace.narrowedLine("application")), text.stderr)
            assertEquals("application", fields.getValue("narrowedTo").jsonPrimitive.content)
            assertFalse(json.stdout.contains("library/"), json.stdout)
        }

    @Test
    fun `an identity of the repository is not narrowed and a stated workspace lists every declaration`() =
        inSharedNameRepositories { workspace ->
            val everywhere = candidates(
                workspace.stated(listOf("symbol", "Twin", "--format", "json", "--quiet")).stdout,
            )
            val other = workspace.application(listOf("symbol", "other.Twin", "--format", "json"))

            assertEquals(0, other.statusCode, other.output)
            assertEquals(3, everywhere.size)
            assertFalse(other.stdout.contains("\"candidates\""), other.stdout)
        }
}
