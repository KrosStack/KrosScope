package io.github.krosstack.kscope.cli.lookup

import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScanNoteTest {
    private val scan = listOf("scan", "--level", "syntax")

    private fun note(outer: Path, folder: Path): String =
        "An index in a parent folder already covers this folder: ${slashed(outer.resolve(".kscope"))}. " +
            "This scan makes a separate index in ${slashed(folder.resolve(".kscope"))}.\n"

    @Test
    fun `a first scan below an index that covers the folder says so and makes its own index`() =
        inNestedWorkspace { nested ->
            val library = nested.root.resolve("lib")

            val first = runIn(library, nested.base, scan)
            val second = runIn(library, nested.base, scan)
            val info = parseJson(nested.run(listOf("info", "--format", "json")).stdout)

            assertEquals(0, first.statusCode, first.output)
            assertTrue(first.stderr.startsWith(note(nested.root, library)), first.stderr)
            assertFalse(second.stderr.contains("parent folder"), second.stderr)
            assertEquals(slashed(library.resolve(".kscope")), info.getValue("indexLocation").jsonPrimitive.content)
        }

    @Test
    fun `a quiet scan says nothing and a json scan keeps its standard output`() = inNestedWorkspace { nested ->
        val library = nested.root.resolve("lib")
        val sources = nested.root.resolve("lib/src")

        val quiet = runIn(library, nested.base, scan + "--quiet")
        val json = runIn(sources, nested.base, scan + listOf("--format", "json"))

        assertEquals("", quiet.stderr)
        assertTrue(Files.isRegularFile(library.resolve(".kscope/index.db")))
        assertTrue(json.stderr.startsWith(note(library, sources)), json.stderr)
        assertEquals("syntax", parseJson(json.stdout).getValue("indexLevel").jsonPrimitive.content)
    }

    @Test
    fun `a scan in a folder without a parent index says nothing`() = inTemporaryFolder { base ->
        val workspace = base.resolve("plain/workspace")
        writeFile(workspace, "README.md", "# Sample\n")

        val result = runIn(workspace, base, scan)

        assertEquals(0, result.statusCode, result.output)
        assertFalse(result.stderr.contains("parent folder"), result.stderr)
    }

    @Test
    fun `below an index of several repositories only a folder of a repository is covered`() =
        inSharedNameRepositories { workspace ->
            val notes = workspace.root.resolve("notes")
            val library = workspace.root.resolve("library/lib")
            writeFile(notes, "Notes.md", "# Notes\n")

            val outside = runIn(notes, workspace.base, scan)
            val inside = runIn(library, workspace.base, scan)

            assertFalse(outside.stderr.contains("parent folder"), outside.stderr)
            assertTrue(inside.stderr.startsWith(note(workspace.root, library)), inside.stderr)
        }
}
