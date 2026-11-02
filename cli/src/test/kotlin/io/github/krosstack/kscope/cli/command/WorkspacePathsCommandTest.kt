package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.APPLICATION_MAIN
import io.github.krosstack.kscope.cli.LIBRARY_RECORD
import io.github.krosstack.kscope.cli.inScannedTwoRepositories
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspacePathsCommandTest {
    private fun run(root: Path, vararg arguments: String) = runKscope(*arguments, "--workspace", root.toString())

    private fun json(root: Path, vararg arguments: String): JsonObject =
        parseJson(run(root, *arguments, "--format", "json").stdout)

    private fun repositoryRelative(path: String): String = path.substringAfter('/')

    @Test
    fun `info lists the repositories of a workspace of two in directory order`() = inScannedTwoRepositories { root ->
        val repositories = json(root, "info").getValue("repositories").jsonArray.map { it.jsonObject }
        val directories = repositories.map { it.getValue("directory").jsonPrimitive.content }
        assertEquals(listOf("application", "library"), directories)
        assertEquals(listOf("application", "library"), repositories.map { it.getValue("name").jsonPrimitive.content })
        assertEquals(listOf("id", "name", "directory", "headRevision"), repositories.first().keys.toList())
    }

    @Test
    fun `info text has a Repositories section for a workspace of two`() = inScannedTwoRepositories { root ->
        val text = run(root, "info").stdout
        assertTrue("Repositories (2)\n" in text, text)
        assertTrue(Regex("(?m)^ +application +[0-9a-f]{8} +1$").containsMatchIn(text), text)
        assertTrue(Regex("(?m)^ +library +[0-9a-f]{8} +1$").containsMatchIn(text), text)
    }

    @Test
    fun `info of a workspace of one repository has no repositories`() = inScannedWorkspace { root ->
        assertFalse("repositories" in json(root, "info").keys)
        assertFalse("Repositories (" in run(root, "info").stdout)
    }

    @Test
    fun `analyze of a file takes its workspace path and prints workspace paths`() = inScannedTwoRepositories { root ->
        val card = run(root, "analyze", LIBRARY_RECORD)
        assertEquals(0, card.statusCode, card.output)
        assertTrue("Declared at  $LIBRARY_RECORD" in card.stdout, card.stdout)
        assertTrue("library/model/src/test/kotlin/org/example/library/RecordTest.kt" in card.stdout, card.stdout)
    }

    @Test
    fun `a path that leaves out the directory is rejected with the workspace paths it could mean`() =
        inScannedTwoRepositories { root ->
            val ambiguous = run(root, "impact", "settings.gradle.kts")
            assertEquals(1, ambiguous.statusCode, ambiguous.output)
            val names = "application/settings.gradle.kts, library/settings.gradle.kts"
            assertTrue(names in ambiguous.stderr, ambiguous.stderr)
            val relative = run(root, "impact", repositoryRelative(LIBRARY_RECORD))
            assertEquals(1, relative.statusCode, relative.output)
            assertTrue(LIBRARY_RECORD in relative.stderr, relative.stderr)
            val analyzed = run(root, "analyze", repositoryRelative(LIBRARY_RECORD))
            assertEquals(1, analyzed.statusCode, analyzed.output)
            assertTrue(LIBRARY_RECORD in analyzed.stderr, analyzed.stderr)
        }

    @Test
    fun `impact names its root by workspace path and keeps the artifact reference in json`() =
        inScannedTwoRepositories { root ->
            val text = run(root, "impact", APPLICATION_MAIN).stdout
            assertTrue("Changed  $APPLICATION_MAIN" in text, text)
            val reference = json(root, "impact", APPLICATION_MAIN)
                .getValue("roots").jsonArray.first().jsonObject.getValue("ref").jsonObject
            assertEquals(repositoryRelative(APPLICATION_MAIN), reference.getValue("path").jsonPrimitive.content)
        }

    @Test
    fun `symbol and related print workspace paths`() = inScannedTwoRepositories { root ->
        assertTrue("$LIBRARY_RECORD:3" in run(root, "symbol", "Record").stdout)
        assertTrue(LIBRARY_RECORD in run(root, "related", LIBRARY_RECORD).stdout)
    }

    @Test
    fun `context has workspace paths in text and markdown and repository paths in json`() =
        inScannedTwoRepositories { root ->
            val task = "change the Record class"
            assertTrue("$LIBRARY_RECORD:" in run(root, "context", task, "--format", "markdown").stdout)
            assertTrue(LIBRARY_RECORD in run(root, "context", task).stdout)
            val paths = json(root, "context", task).getValue("fragments").jsonArray
                .map { it.jsonObject.getValue("artifact").jsonObject.getValue("path").jsonPrimitive.content }
            assertTrue(repositoryRelative(LIBRARY_RECORD) in paths, paths.toString())
        }
}
