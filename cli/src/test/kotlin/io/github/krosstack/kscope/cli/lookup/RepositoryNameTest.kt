package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ExitCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepositoryNameTest {
    private fun named(name: String) = listOf(
        listOf("symbol", name),
        listOf("impact", name),
        listOf("related", name),
        listOf("graph", "--scope", "neighborhood", "--node", name),
        listOf("diagram", "symbol", name),
    )

    private fun choice(workspace: NestedWorkspace, name: String, repository: String): String =
        "Using the declaration of \"$name\" in $repository, the repository of the current folder. " +
            "Other repositories declare this name too; --workspace ${slashed(workspace.root)} lists them all.\n"

    @Test
    fun `the one declaration of a shared name in the current repository is used and the choice is said`() =
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application/lib")
            (named("Shared") + named("Shared").map { it + listOf("--format", "json") }).forEach { query ->
                val inside = runIn(application, workspace.base, query + "--quiet")
                val everywhere = workspace.stated(query + "--quiet")

                assertEquals(0, inside.statusCode, "$query: ${inside.output}")
                assertEquals(ExitCodes.USAGE, everywhere.statusCode, "$query: ${everywhere.output}")
                assertTrue(inside.stderr.endsWith(choice(workspace, "Shared", "application")), inside.stderr)
                assertFalse(everywhere.stderr.contains("Using the declaration"), everywhere.stderr)
            }
        }

    @Test
    fun `two declarations of a shared name in the current repository stay ambiguous as before`() =
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application")
            named("Twin").filter { it.first() != "symbol" }.forEach { query ->
                val inside = runIn(application, workspace.base, query + "--quiet")
                val everywhere = workspace.stated(query + "--quiet")

                assertEquals(ExitCodes.USAGE, inside.statusCode, "$query: ${inside.output}")
                assertEquals(everywhere.stdout, inside.stdout, query.toString())
                assertFalse(inside.stderr.contains("Using the declaration"), inside.stderr)
            }
        }

    @Test
    fun `the card of a shared name shows the declaration of the current repository and says so`() =
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application")
            val library = workspace.root.resolve("library")

            val inApplication = runIn(application, workspace.base, listOf("analyze", "Shared", "--quiet"))
            val inLibrary = runIn(library, workspace.base, listOf("analyze", "Shared", "--quiet"))

            assertTrue(inApplication.stdout.contains("$APPLICATION_SHARED:1"), inApplication.stdout)
            assertFalse(inApplication.stdout.contains("library/lib/src/main/kotlin/Shared.kt"), inApplication.stdout)
            assertTrue(inApplication.stderr.endsWith(choice(workspace, "Shared", "application")), inApplication.stderr)
            assertTrue(inLibrary.stdout.contains("library/lib/src/main/kotlin/Shared.kt:1"), inLibrary.stdout)
            assertTrue(inLibrary.stderr.endsWith(choice(workspace, "Shared", "library")), inLibrary.stderr)
        }

    @Test
    fun `a name of another repository is found and a folder of no repository chooses none`() =
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application")
            val notes = workspace.root.resolve("notes")

            val record = runIn(application, workspace.base, listOf("impact", "Record", "--quiet"))
            val shared = runIn(notes, workspace.base, listOf("impact", "Shared", "--quiet"))

            assertEquals(0, record.statusCode, record.output)
            assertEquals(workspace.stated(listOf("impact", "Record", "--quiet")).stdout, record.stdout)
            assertFalse(record.stderr.contains("Using the declaration"), record.stderr)
            assertEquals(ExitCodes.USAGE, shared.statusCode, shared.output)
            assertTrue(shared.stderr.startsWith(workspace.line + "\n"), shared.stderr)
            assertFalse(shared.stderr.contains("Using the declaration"), shared.stderr)
        }

    @Test
    fun `a task asked inside a repository is answered from that repository`() {
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application")
            listOf(listOf("analyze", "change the shared record"), listOf("context", "change the shared record"))
                .forEach { query ->
                    val inside = runIn(application, workspace.base, query + "--quiet")
                    val everywhere = workspace.stated(query + "--quiet")

                    assertEquals(0, inside.statusCode, "$query: ${inside.output}")
                    assertFalse(inside.stdout.contains("library/"), inside.stdout)
                    assertTrue(everywhere.stdout.contains("library/"), everywhere.stdout)
                    assertTrue(inside.stderr.startsWith(workspace.narrowedLine("application")), inside.stderr)
                }
        }
    }
}
