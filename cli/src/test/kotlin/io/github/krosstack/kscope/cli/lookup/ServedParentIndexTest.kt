package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ExitCodes
import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.withTestServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServedParentIndexTest {
    private val queries = listOf(
        listOf("info"),
        listOf("analyze", "change the library"),
        listOf("context", "change the library", "--estimator", "chars"),
        listOf("symbol", "Lib"),
        listOf("impact", "Lib.kt"),
        listOf("related", "Lib"),
        listOf("graph"),
    )

    @Test
    fun `a server of the parent folder answers a subfolder as the command line does`() = inNestedWorkspace { nested ->
        withTestServer(nested.root) { server ->
            for (query in queries) {
                for (format in listOf("text", "json")) {
                    val arguments = query + listOf("--format", format, "--quiet")
                    val local = runAt(nested.folder, listOf("--no-server") + arguments)
                    val remote = runAt(nested.folder, arguments)

                    assertEquals(0, remote.statusCode, "$arguments: ${remote.output}")
                    assertEquals(local.stdout, remote.stdout, arguments.toString())
                    assertEquals(local.stderr, remote.stderr, arguments.toString())
                }
            }
            assertEquals(queries.size * 2, server.requestCount)
        }
    }

    @Test
    fun `a served folder is taken without an index that the client could open`() = inTemporaryFolder { base ->
        val root = Files.createDirectories(base.resolve("served"))
        val folder = Files.createDirectories(root.resolve("a/b"))
        withTestServer(root) { server ->
            val remote = runAt(folder, listOf("symbol", "Lib", "--quiet"))

            assertEquals(1, server.requestCount)
            assertEquals(ExitCodes.FAILURE, remote.statusCode, remote.output)
            assertTrue(remote.stderr.endsWith(NO_INDEX_LINE), remote.stderr)
            assertTrue(remote.stderr.startsWith("Using the index of a parent folder: ${slashed(root)}/"), remote.stderr)
        }
    }

    @Test
    fun `the line about the parent index comes from the server once`() = inNestedWorkspace { nested ->
        withTestServer(nested.root) { server ->
            val remote = runAt(nested.folder, listOf("symbol", "Lib", "--quiet"))

            assertEquals(1, server.requestCount)
            assertTrue(remote.stderr.startsWith(nested.line + "\n"), remote.stderr)
            assertEquals(1, remote.stderr.split("parent folder").size - 1)
        }
    }
}
