package io.github.krosstack.kscope.cli.freshness

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.lookup.LIB_FILE
import io.github.krosstack.kscope.cli.lookup.NestedWorkspace
import io.github.krosstack.kscope.cli.lookup.git
import io.github.krosstack.kscope.cli.lookup.inNestedWorkspace
import io.github.krosstack.kscope.cli.lookup.inSharedNameRepositories
import io.github.krosstack.kscope.cli.lookup.runIn
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.runAt
import io.github.krosstack.kscope.cli.serve.watch.WatchServerInputs
import io.github.krosstack.kscope.cli.serve.withTestServer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BehindLineTest {
    private val warned = listOf(
        listOf("analyze", "change the library"),
        listOf("analyze", "Lib"),
        listOf("context", "change the library", "--estimator", "chars"),
        listOf("impact", "Lib"),
    )
    private val silent = listOf(listOf("symbol", "Lib"), listOf("related", "Lib"), listOf("graph"))
    private val json = listOf("--format", "json", "--quiet")
    private val record = "lib/src/main/kotlin/Record.kt"
    private val twin = "lib/src/main/kotlin/Twin.kt"

    private fun changedAfterScan(root: Path, file: String, text: String? = null): String {
        val times = parseJson(Files.readString(root.resolve(".kscope/scan.json")))
        val started = Instant.parse(times.getValue("startedAt").jsonPrimitive.content)
        text?.let { Files.writeString(root.resolve(file), it) }
        Files.setLastModifiedTime(root.resolve(file), FileTime.from(started.plusSeconds(60)))
        val finished = Instant.parse(times.getValue("finishedAt").jsonPrimitive.content)
        return finished.truncatedTo(ChronoUnit.SECONDS).toString()
    }

    private fun counts(freshness: JsonObject, name: String): List<Int> =
        freshness.getValue(name).jsonObject.values.map { it.jsonPrimitive.int }

    private fun freshnessFrom(workspace: NestedWorkspace, folder: Path): JsonObject =
        parseJson(runIn(folder, workspace.base, listOf("impact", "Record") + json).stdout)
            .getValue("indexFreshness").jsonObject

    @Test
    fun `analyze context and impact say after their answer that the index is behind`() = inNestedWorkspace { nested ->
        val before = warned.map { nested.stated(it + "--quiet") }
        val scan = changedAfterScan(nested.root, LIB_FILE)
        val line = "Index is behind: 1 changed file, estimated from file times, since the scan of $scan.\n"

        warned.forEachIndexed { index, query ->
            val after = nested.stated(query + "--quiet")

            assertEquals(0, after.statusCode, "$query: ${after.output}")
            assertEquals(before[index].stdout, after.stdout, query.toString())
            assertEquals(before[index].stderr + line, after.stderr, query.toString())
            assertFalse(before[index].stderr.contains("behind"), before[index].stderr)
        }
    }

    @Test
    fun `json carries the freshness as a field only when the index is behind`() = inNestedWorkspace { nested ->
        val before = warned.map { parseJson(nested.stated(it + json).stdout) }
        changedAfterScan(nested.root, LIB_FILE)

        warned.forEachIndexed { index, query ->
            val result = nested.stated(query + json)
            val after = parseJson(result.stdout)
            val freshness = after.getValue("indexFreshness").jsonObject

            assertEquals(listOf("schemaVersion", "indexLevel", "indexFreshness"), after.keys.take(3), query.toString())
            assertEquals(before[index], JsonObject(after - "indexFreshness"), query.toString())
            assertEquals("behind", freshness.getValue("state").jsonPrimitive.content)
            assertEquals(1, freshness.getValue("changedFiles").jsonPrimitive.int)
            assertTrue(freshness.getValue("estimate").jsonPrimitive.boolean)
            assertEquals("", result.stderr, query.toString())
        }
    }

    @Test
    fun `symbol related and graph do not look at the freshness`() = inNestedWorkspace { nested ->
        changedAfterScan(nested.root, LIB_FILE)

        silent.forEach { query ->
            val text = nested.stated(query + "--quiet")
            val structured = parseJson(nested.stated(query + json).stdout)

            assertFalse(text.stderr.contains("behind"), text.stderr)
            assertFalse("indexFreshness" in structured, query.toString())
        }
    }

    @Test
    fun `info shows when the index was scanned and each outcome with its count`() = inNestedWorkspace { nested ->
        val fresh = nested.stated(listOf("info"))
        val scan = changedAfterScan(nested.root, LIB_FILE)
        val stale = nested.stated(listOf("info"))
        val structured = parseJson(nested.stated(listOf("info", "--format", "json")).stdout)
        val freshness = structured.getValue("indexFreshness").jsonObject

        val changed = "1 behind, 0 not behind, 0 not checked \\(1 changed file, estimated from file times\\)\n"
        assertTrue(Regex("Scanned +$scan\n").containsMatchIn(fresh.stdout), fresh.stdout)
        assertTrue(Regex("Commits +0 behind, 0 not behind, 0 not checked\n").containsMatchIn(fresh.stdout))
        assertTrue(Regex("Working trees +0 behind, 1 not behind, 0 not checked\n").containsMatchIn(fresh.stdout))
        assertTrue(Regex("Working trees +$changed").containsMatchIn(stale.stdout), stale.stdout)
        assertEquals("behind", freshness.getValue("state").jsonPrimitive.content)
        assertTrue(freshness.getValue("scannedAt").jsonPrimitive.content.startsWith(scan.removeSuffix("Z")))
        assertFalse(stale.stderr.contains("behind"), stale.stderr)
    }

    @Test
    fun `from the root of several repositories a commit is seen and no working tree is compared`() =
        inSharedNameRepositories { workspace ->
            val library = workspace.root.resolve("library")
            val scan = changedAfterScan(workspace.root, "application/$twin", "class Twin { val changed = 1 }\n")
            val clean = workspace.stated(listOf("impact", "Record", "--quiet"))
            Files.writeString(library.resolve(record), "class Record { val changed = 1 }\n")
            git(library, listOf("add", "-A"))
            git(library, listOf("-c", "user.name=test", "-c", "user.email=test@example.org", "commit", "-q", "-m", "x"))

            val result = workspace.stated(listOf("impact", "Record", "--quiet"))
            val freshness = freshnessFrom(workspace, workspace.root)

            val line = "Index is behind: 1 repository at another commit since the scan of $scan.\n"
            assertFalse(clean.stderr.contains("behind"), clean.stderr)
            assertEquals(0, result.statusCode, result.output)
            assertTrue(result.stderr.endsWith(line), result.stderr)
            assertEquals(listOf(1, 1, 0), counts(freshness, "commits"))
            assertEquals(listOf(0, 0, 2), counts(freshness, "workingTrees"))
        }

    @Test
    fun `from inside a repository its changed files are counted and those of another repository are not`() =
        inSharedNameRepositories { workspace ->
            val application = workspace.root.resolve("application")
            changedAfterScan(workspace.root, "library/$record", "class Record { val changed = 1 }\n")
            val other = runIn(application, workspace.base, listOf("impact", "Record", "--quiet"))
            val scan = changedAfterScan(workspace.root, "application/$twin", "class Twin { val changed = 1 }\n")

            val own = runIn(application.resolve("lib"), workspace.base, listOf("impact", "Record", "--quiet"))
            val freshness = freshnessFrom(workspace, application)

            assertFalse(other.stderr.contains("behind"), other.stderr)
            assertEquals(0, own.statusCode, own.output)
            assertTrue(own.stderr.endsWith("Index is behind: 1 changed file since the scan of $scan.\n"), own.stderr)
            assertEquals("behind", freshness.getValue("state").jsonPrimitive.content)
            assertEquals(listOf(0, 2, 0), counts(freshness, "commits"))
            assertEquals(listOf(1, 0, 1), counts(freshness, "workingTrees"))
        }

    @Test
    fun `a server without a watcher gives the line that the command line gives`() = inNestedWorkspace { nested ->
        withTestServer(nested.root) { server ->
            changedAfterScan(nested.root, LIB_FILE)
            for (arguments in listOf(listOf("impact", "Lib", "--quiet"), listOf("impact", "Lib") + json)) {
                val local = runAt(nested.root, listOf("--no-server") + arguments)
                val remote = runAt(nested.root, arguments)

                assertEquals(local.stdout, remote.stdout, arguments.toString())
                assertEquals(local.stderr, remote.stderr, arguments.toString())
                assertTrue((remote.stdout + remote.stderr).contains("ehind"), remote.output)
            }
            assertEquals(2, server.requestCount)
        }
    }

    @Test
    fun `a watching server keeps its own line and no second one is added`() = inScannedWorkspace { root ->
        val inputs = WatchServerInputs()
        withTestServer(root, inputs.settings()) { server ->
            inputs.awaitFirstPoll()
            assertEquals(0, server.query(listOf("scan", "--quiet")).exitCode)
            inputs.poll(server, 0)
            changedAfterScan(root, "lib/src/main/kotlin/Lib.kt")

            val answer = server.query(listOf("impact", "Lib", "--quiet"))
            val structured = parseJson(server.query(listOf("impact", "Lib") + json).stdout)

            assertEquals("Index is behind: changes are settling.\n", answer.stderr)
            assertFalse("indexFreshness" in structured)
        }
    }
}
