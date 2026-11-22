package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NarrowedSymbolAnswersTest {
    private val fieldNames = listOf("narrowedTo", "outsideNarrowing", "widenedFrom", "incomplete")

    private fun NestedWorkspace.application(arguments: List<String>) =
        runIn(root.resolve(APPLICATION_FOLDER), base, arguments + "--quiet")

    @Test
    fun `an identity outside the narrowing is shown with a line that says where it lies`() {
        inQualifiedTaskRepositories { workspace ->
            val text = workspace.application(listOf("symbol", "sync.TaskSync"))
            val json = workspace.application(listOf("symbol", "sync.TaskSync", "--format", "json"))
            val fields = parseJson(json.stdout)

            assertEquals(0, text.statusCode, text.output)
            assertTrue(text.stdout.contains("TaskSync"), text.stdout)
            val outside = workspace.narrowedLine("application").trimEnd() +
                " \"sync.TaskSync\" lies in library/, outside that limit.\n"
            assertTrue(text.stderr.startsWith(outside), text.stderr)
            assertEquals("application", fields.getValue("narrowedTo").jsonPrimitive.content)
            assertEquals("library", fields.getValue("outsideNarrowing").jsonPrimitive.content)
            assertNull(fields["widenedFrom"])
            assertEquals(listOf("indexLocation", "narrowedTo", "outsideNarrowing"), fields.keys.drop(2).take(3))
        }
    }

    @Test
    fun `a simple name that a declaration inside the narrowing bears is a narrowed search`() {
        inQualifiedTaskRepositories { workspace ->
            val text = workspace.application(listOf("symbol", "TaskStore"))
            val json = parseJson(workspace.application(listOf("symbol", "TaskStore", "--format", "json")).stdout)

            assertTrue(text.stderr.startsWith(workspace.narrowedLine("application")), text.stderr)
            assertTrue(text.stdout.contains("TaskReminders"), text.stdout)
            assertFalse(text.stdout.contains("TaskSync"), text.stdout)
            assertEquals("application", json.getValue("narrowedTo").jsonPrimitive.content)
            assertNull(json["outsideNarrowing"])
            assertNull(json["widenedFrom"])
        }
    }

    @Test
    fun `a simple name that only a declaration outside the narrowing bears widens the search and says so`() {
        inQualifiedTaskRepositories { workspace ->
            val text = workspace.application(listOf("symbol", "TaskSync"))
            val json = workspace.application(listOf("symbol", "TaskSync", "--format", "json"))
            val fields = parseJson(json.stdout)

            assertEquals(0, text.statusCode, text.output)
            assertTrue(text.stderr.startsWith(workspace.widenedLine("application", "TaskSync")), text.stderr)
            assertTrue(text.stdout.contains("TaskSync") && text.stdout.contains("TaskStore"), text.stdout)
            assertEquals("application", fields.getValue("widenedFrom").jsonPrimitive.content)
            assertNull(fields["narrowedTo"])
            assertNull(fields["outsideNarrowing"])
        }
    }

    @Test
    fun `a stated workspace shows the same declaration with none of these fields`() {
        inQualifiedTaskRepositories { workspace ->
            val folder = workspace.root.resolve(APPLICATION_FOLDER)
            val arguments = listOf("symbol", "TaskSync", "--format", "json", "--quiet")
            val json = runIn(folder, workspace.base, arguments + listOf("--workspace", workspace.root.toString()))
            val fields = parseJson(json.stdout)

            assertTrue(json.stdout.contains("TaskSync"), json.stdout)
            assertTrue(fieldNames.none { it in fields })
        }
    }
}
