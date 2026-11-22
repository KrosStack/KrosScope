package io.github.krosstack.kscope.cli.freshness

import io.github.krosstack.kscope.cli.lookup.git
import io.github.krosstack.kscope.cli.lookup.inNestedWorkspace
import io.github.krosstack.kscope.cli.lookup.inSharedNameRepositories
import io.github.krosstack.kscope.cli.lookup.runIn
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BehindLineRemedyTest {
    private val json = listOf("--format", "json", "--quiet")
    private val record = "lib/src/main/kotlin/Record.kt"

    @Test
    fun `an index without the time of its scan says what to do`() = inNestedWorkspace { nested ->
        Files.delete(nested.root.resolve(".kscope/scan.json"))

        val result = nested.stated(listOf("impact", "Lib", "--quiet"))

        val line = "Index may be behind: the time of the scan is not known; the next scan records it.\n"
        assertEquals(0, result.statusCode, result.output)
        assertTrue(result.stderr.endsWith(line), result.stderr)
    }

    @Test
    fun `a scan without its changed files and a file changed after it say that at least that file is behind`() =
        inSharedNameRepositories { workspace ->
            val library = workspace.root.resolve("library")
            val scanFile = workspace.root.resolve(".kscope/scan.json")
            val times = parseJson(Files.readString(scanFile))
            val started = Instant.parse(times.getValue("startedAt").jsonPrimitive.content)
            val finished = times.getValue("finishedAt").jsonPrimitive.content
            Files.writeString(scanFile, """{"format":1,"startedAt":"$started","finishedAt":"$finished"}""")
            Files.writeString(library.resolve(record), "class Record { val changed = 1 }\n")
            Files.setLastModifiedTime(library.resolve(record), FileTime.from(started.plusSeconds(60)))

            val result = runIn(library.resolve("lib"), workspace.base, listOf("impact", "Record", "--quiet"))

            val scan = Instant.parse(finished).truncatedTo(ChronoUnit.SECONDS)
            val reason = "the scan did not record its changed files; the next scan records them"
            val line = "Index is behind: at least 1 changed file since the scan of $scan; $reason.\n"
            assertEquals(0, result.statusCode, result.output)
            assertTrue(result.stderr.endsWith(line), result.stderr)
        }

    @Test
    fun `a server started before a commit gives the answer of the command line afterwards`() =
        inSharedNameRepositories { workspace ->
            val library = workspace.root.resolve("library")
            withTestServer(workspace.root) { server ->
                val clean = runAt(workspace.root, listOf("impact", "Record", "--quiet"))
                Files.writeString(library.resolve(record), "class Record { val changed = 1 }\n")
                git(library, listOf("add", "-A"))
                val identity = listOf("-c", "user.name=test", "-c", "user.email=test@example.org")
                git(library, identity + listOf("commit", "-q", "-m", "x"))
                for (arguments in listOf(listOf("impact", "Record", "--quiet"), listOf("impact", "Record") + json)) {
                    val local = runAt(workspace.root, listOf("--no-server") + arguments)
                    val remote = runAt(workspace.root, arguments)

                    assertEquals(local.stdout, remote.stdout, arguments.toString())
                    assertEquals(local.stderr, remote.stderr, arguments.toString())
                    assertEquals(local.statusCode, remote.statusCode, arguments.toString())
                    assertTrue((remote.stdout + remote.stderr).contains("ehind"), remote.output)
                }
                assertFalse(clean.stderr.contains("behind"), clean.stderr)
                assertEquals(3, server.requestCount)
            }
        }
}
