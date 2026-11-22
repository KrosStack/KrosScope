package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServedNarrowedAnswersTest {
    private val task = "change the task sync clock"
    private val queries = listOf(
        listOf("analyze", task),
        listOf("context", task, "--estimator", "chars"),
        listOf("symbol", "TaskStore"),
    )

    @Test
    fun `a server answers a folder of one repository as the command line does`() {
        inTaskRepositories { workspace ->
            val folder = workspace.root.resolve(APPLICATION_FOLDER)
            withTestServer(workspace.root) { server ->
                for (query in queries) {
                    for (format in listOf("text", "json")) {
                        val arguments = query + listOf("--format", format, "--quiet")
                        val local = runAt(folder, listOf("--no-server") + arguments)
                        val remote = runAt(folder, arguments)

                        assertEquals(0, remote.statusCode, "$arguments: ${remote.output}")
                        assertEquals(local.stdout, remote.stdout, arguments.toString())
                        assertEquals(local.stderr, remote.stderr, arguments.toString())
                    }
                }
                assertEquals(queries.size * 2, server.requestCount)
            }
        }
    }

    @Test
    fun `a server answers an identity outside the narrowing and a widened name as the command line does`() {
        inQualifiedTaskRepositories { workspace ->
            val folder = workspace.root.resolve(APPLICATION_FOLDER)
            val widening = listOf(listOf("symbol", "sync.TaskSync"), listOf("symbol", "TaskSync"))
            withTestServer(workspace.root) { server ->
                for (query in widening) {
                    for (format in listOf("text", "json")) {
                        val arguments = query + listOf("--format", format, "--quiet")
                        val local = runAt(folder, listOf("--no-server") + arguments)
                        val remote = runAt(folder, arguments)

                        assertEquals(0, remote.statusCode, "$arguments: ${remote.output}")
                        assertEquals(local.stdout, remote.stdout, arguments.toString())
                        assertEquals(local.stderr, remote.stderr, arguments.toString())
                    }
                }
                assertEquals(widening.size * 2, server.requestCount)
            }
        }
    }

    @Test
    fun `the line of a narrowed answer comes from the server once`() {
        inTaskRepositories { workspace ->
            val folder = workspace.root.resolve(APPLICATION_FOLDER)
            withTestServer(workspace.root) { _ ->
                val remote = runAt(folder, listOf("analyze", task, "--quiet"))

                assertTrue(remote.stderr.startsWith(workspace.narrowedLine("application")), remote.stderr)
                assertEquals(1, remote.stderr.split("parent folder").size - 1)
            }
        }
    }
}
