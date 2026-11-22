package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.parseJson
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NarrowedAnswersTest {
    private val task = "change the task sync clock"
    private val leading = listOf("schemaVersion", "indexLevel", "indexLocation", "narrowedTo")

    private fun NestedWorkspace.application(arguments: List<String>) =
        runIn(root.resolve(APPLICATION_FOLDER), base, arguments + "--quiet")

    @Test
    fun `a task in one repository is answered from that repository and the line says so`() {
        inTaskRepositories { workspace ->
            val inside = workspace.application(listOf("analyze", task))
            val everywhere = workspace.stated(listOf("analyze", task, "--quiet"))

            assertEquals(0, inside.statusCode, inside.output)
            assertTrue(inside.stderr.startsWith(workspace.narrowedLine("application")), inside.stderr)
            assertTrue(inside.stdout.contains("application/"), inside.stdout)
            assertFalse(inside.stdout.contains("TaskSync"), inside.stdout)
            assertTrue(everywhere.stdout.contains("TaskSync"), everywhere.stdout)
        }
    }

    @Test
    fun `the json of a narrowed task names the repository after the location of the index`() {
        inTaskRepositories { workspace ->
            val inside = workspace.application(listOf("analyze", task, "--format", "json"))
            val json = parseJson(inside.stdout)

            assertEquals(leading, json.keys.take(leading.size))
            assertEquals("application", json.getValue("narrowedTo").jsonPrimitive.content)
            assertFalse(inside.stdout.contains("TaskSync"), inside.stdout)
            assertFalse(inside.stderr.contains("parent folder"), inside.stderr)
        }
    }

    @Test
    fun `context in one repository reads only that repository`() {
        inTaskRepositories { workspace ->
            val query = listOf("context", task, "--estimator", "chars")
            val inside = workspace.application(query + listOf("--format", "markdown"))
            val everywhere = workspace.stated(query + listOf("--format", "markdown", "--quiet"))
            val json = parseJson(workspace.application(query + listOf("--format", "json")).stdout)

            assertTrue(inside.stderr.startsWith(workspace.narrowedLine("application")), inside.stderr)
            assertFalse(inside.stdout.contains("TaskSync"), inside.stdout)
            assertTrue(everywhere.stdout.contains("TaskSync"), everywhere.stdout)
            assertEquals("application", json.getValue("narrowedTo").jsonPrimitive.content)
        }
    }

    @Test
    fun `symbol matches come from one repository and the named symbol is kept`() {
        inTaskRepositories { workspace ->
            val query = listOf("symbol", "TaskStore")
            val inside = workspace.application(query + listOf("--format", "json"))
            val everywhere = workspace.stated(query + listOf("--format", "json", "--quiet"))
            val named = workspace.application(listOf("symbol", "TaskSync", "--format", "json"))

            assertEquals("application", parseJson(inside.stdout).getValue("narrowedTo").jsonPrimitive.content)
            assertTrue(inside.stdout.contains("TaskReminders"), inside.stdout)
            assertFalse(inside.stdout.contains("TaskSync"), inside.stdout)
            assertTrue(everywhere.stdout.contains("TaskSync"), everywhere.stdout)
            assertTrue(named.stdout.contains("TaskSync"), named.stdout)
        }
    }

    @Test
    fun `a stated workspace is never narrowed`() {
        inTaskRepositories { workspace ->
            val folder = workspace.root.resolve(APPLICATION_FOLDER)
            val stated = listOf("--workspace", workspace.root.toString())
            val result = runIn(folder, workspace.base, listOf("analyze", task, "--format", "json", "--quiet") + stated)
            val json = parseJson(result.stdout)

            assertNull(json["narrowedTo"])
            assertNull(json["indexLocation"])
            assertTrue(result.stdout.contains("TaskSync"), result.stdout)
            assertFalse(result.stderr.contains("parent folder"), result.stderr)
        }
    }

    @Test
    fun `a folder that belongs to no repository is answered from the whole workspace and the line says so`() {
        inTaskRepositories { workspace ->
            val notes = workspace.root.resolve("notes")
            val queries = listOf(listOf("analyze", task), listOf("context", task, "--estimator", "chars"))
            queries.forEach { query ->
                val json = runIn(notes, workspace.base, query + listOf("--format", "json", "--quiet"))
                val text = runIn(notes, workspace.base, query + "--quiet")

                assertNull(parseJson(json.stdout)["narrowedTo"], query.toString())
                assertTrue(json.stdout.contains("TaskSync"), query.toString())
                assertTrue(text.stderr.startsWith(workspace.line + "\n"), text.stderr)
            }
        }
    }

    @Test
    fun `an index of one repository is not narrowed`() {
        inNestedWorkspace { nested ->
            val json = nested.run(listOf("analyze", "change the library", "--format", "json", "--quiet"))
            val text = nested.run(listOf("analyze", "change the library", "--quiet"))

            assertNull(parseJson(json.stdout)["narrowedTo"])
            assertTrue(text.stderr.startsWith(nested.line + "\n"), text.stderr)
        }
    }

    @Test
    fun `the lists of other commands are not narrowed`() {
        inTaskRepositories { workspace ->
            listOf(listOf("impact", "TaskSync"), listOf("related", "TaskSync")).forEach { query ->
                val inside = workspace.application(query)
                val everywhere = workspace.stated(query + "--quiet")

                assertEquals(everywhere.stdout, inside.stdout, query.toString())
                assertTrue(inside.stderr.startsWith(workspace.line + "\n"), inside.stderr)
            }
        }
    }
}
