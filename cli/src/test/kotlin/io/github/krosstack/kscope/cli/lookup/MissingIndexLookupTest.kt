package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.ExitCodes
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissingIndexLookupTest {
    private val queries = listOf(
        listOf("info"),
        listOf("analyze", "change the library"),
        listOf("context", "change the library"),
        listOf("symbol", "Lib"),
        listOf("impact", "Lib"),
        listOf("related", "Lib"),
        listOf("graph"),
        listOf("analyze", "change the library", "--sections", "tests"),
    )

    @Test
    fun `without an index in the folder or above it the error is the one line it was`() = inTemporaryFolder { base ->
        val folder = Files.createDirectories(base.resolve("plain/a"))
        (queries + queries.dropLast(1).map { it + listOf("--format", "json") }).forEach { query ->
            val result = runIn(folder, base, query + "--quiet")

            assertEquals(ExitCodes.FAILURE, result.statusCode, "$query: ${result.output}")
            assertEquals(NO_INDEX_LINE, result.stderr, query.toString())
            assertEquals("", result.stdout, query.toString())
        }
        assertTrue(Files.notExists(folder.resolve(".kscope")) && Files.notExists(base.resolve(".kscope")))
    }

    @Test
    fun `the folders one and two levels below that have an index are named`() = inTemporaryFolder { base ->
        val folder = Files.createDirectories(base.resolve("plain"))
        listOf("group/tool", "app", "a/b/deep", "build/output").forEach { scanned(folder.resolve(it)) }
        val named = "Folders below with an index: app, group/tool. Pass one with --workspace.\n"

        queries.forEach { query ->
            val result = runIn(folder, base, query + "--quiet")

            assertEquals(ExitCodes.FAILURE, result.statusCode, "$query: ${result.output}")
            assertEquals(NO_INDEX_LINE + named, result.stderr, query.toString())
            assertEquals("", result.stdout, query.toString())
        }
    }

    @Test
    fun `an index above the stop folder is neither used by a read command nor named by a scan`() =
        inNestedWorkspace { nested ->
            val stop = nested.root.resolve("lib")

            val read = runIn(nested.folder, stop, listOf("info", "--quiet"))
            val scan = runIn(nested.folder, stop, listOf("scan", "--level", "syntax"))

            assertEquals(ExitCodes.FAILURE, read.statusCode, read.output)
            assertEquals(NO_INDEX_LINE, read.stderr)
            assertEquals(0, scan.statusCode, scan.output)
            assertFalse(scan.stderr.contains("parent folder"), scan.stderr)
        }

    @Test
    fun `more than five folders below end with a count`() {
        val folder = Path.of("work").toAbsolutePath()
        val below = listOf("a", "b", "c", "d", "e", "f", "g").map { folder.resolve(it).resolve("x") }

        val message = IndexNotes.missing(folder, below)

        assertEquals(
            "No index found. Run `kscope scan` first.\n" +
                "Folders below with an index: a/x, b/x, c/x, d/x, e/x and 2 more. Pass one with --workspace.",
            message,
        )
        assertEquals("No index found. Run `kscope scan` first.", IndexNotes.missing(folder, emptyList()))
    }
}
