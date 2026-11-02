package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.cli.serve.query
import io.github.krosstack.kscope.cli.serve.withTestServer
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `kscope.toml` stops a scan when it is not valid, shows in `info`, and leaves every query alone. */
class SettingsFileCommandTest {
    private val invalid = "format = 1\n\n[resources]\nworkers = 99\nlevel = 2\n"
    private val firstError = "kscope.toml:4: resources.workers: must be between 1 and 8"
    private val secondError = "kscope.toml:5: resources.level: unknown key (valid keys: workers, worker-heap-mib)"

    private fun indexOf(root: Path): List<Byte> = Files.readAllBytes(root.resolve(".kscope/index.db")).toList()

    @Test
    fun `an invalid file stops scan and update with exit code 1 and the errors on standard error`() =
        inScannedWorkspace { root ->
            val before = indexOf(root)
            writeFile(root, "kscope.toml", invalid)

            for (arguments in listOf(listOf("scan"), listOf("update"), listOf("scan", "--rebuild", "--verify"))) {
                val result = runKscopeWith(arguments + listOf("--workspace", root.toString()))

                assertEquals(1, result.statusCode, arguments.toString())
                assertEquals("", result.stdout, arguments.toString())
                assertTrue(result.stderr.startsWith("error: kscope.toml is not valid, so the scan did not start:"))
                assertTrue(firstError in result.stderr && secondError in result.stderr, result.stderr)
            }
            assertEquals(before, indexOf(root))
        }

    @Test
    fun `every query still answers and info reports the errors with exit code 0`() = inScannedWorkspace { root ->
        writeFile(root, "kscope.toml", invalid)

        for (arguments in listOf(listOf("analyze", "lib"), listOf("symbol", "Lib"), listOf("graph"))) {
            val result = runKscopeWith(arguments + listOf("--workspace", root.toString()))
            assertEquals(0, result.statusCode, "$arguments: ${result.output}")
        }
        val text = runKscope("info", "--workspace", root.toString())

        assertEquals(0, text.statusCode, text.output)
        assertTrue("  File         kscope.toml (not valid, so a scan will not start)\n" in text.stdout, text.stdout)
        assertTrue("  Problems     $firstError\n" in text.stdout, text.stdout)
        assertTrue("               $secondError\n" in text.stdout, text.stdout)
    }

    @Test
    fun `info reports the errors in its json result`() = inScannedWorkspace { root ->
        writeFile(root, "kscope.toml", invalid)

        val result = runKscope("info", "--format", "json", "--workspace", root.toString())

        assertEquals(0, result.statusCode, result.output)
        val settings = parseJson(result.stdout).getValue("settings").jsonObject
        assertEquals("kscope.toml", settings.getValue("file").jsonPrimitive.content)
        assertEquals("false", settings.getValue("valid").jsonPrimitive.content)
        val errors = settings.getValue("errors").jsonArray.map { it.jsonObject }
        assertEquals(listOf(4, 5), errors.map { it.getValue("line").jsonPrimitive.int })
        val keys = errors.map { it.getValue("key").jsonPrimitive.content }
        assertEquals(listOf("resources.workers", "resources.level"), keys)
    }

    @Test
    fun `info shows a valid file and where each value comes from`() = inScannedWorkspace { root ->
        writeFile(root, "kscope.toml", "[resources]\nworkers = 4\nworker-heap-mib = 1024\n")

        val text = runKscope("info", "--workspace", root.toString())
        val json = runKscope("info", "--format", "json", "--workspace", root.toString())

        assertTrue("\nSettings\n  File         kscope.toml\n" in text.stdout, text.stdout)
        assertTrue("  Workers      4 (kscope.toml)\n" in text.stdout, text.stdout)
        assertTrue("  Worker heap  1,024 MiB (kscope.toml)" in text.stdout, text.stdout)
        val resources = parseJson(json.stdout).getValue("settings").jsonObject.getValue("resources").jsonObject
        assertEquals(4, resources.getValue("workers").jsonObject.getValue("value").jsonPrimitive.int)
        assertEquals("kscope.toml", resources.getValue("workers").jsonObject.getValue("origin").jsonPrimitive.content)
    }

    @Test
    fun `info shows scan settings and whether the index was built with them`() = inScannedWorkspace { root ->
        val plain = runKscope("info", "--workspace", root.toString())
        assertTrue("  Scan " !in plain.stdout, plain.stdout)

        writeFile(root, "kscope.toml", "[scan]\nexclude = [\"docs\"]\ntests = false\n")
        val outdated = runKscope("info", "--workspace", root.toString())
        val outdatedLine = "  Scan         exclude docs; tests off (index was built with other settings, scan again)\n"
        assertTrue(outdatedLine in outdated.stdout, outdated.stdout)

        assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
        val current = runKscope("info", "--workspace", root.toString())
        assertTrue("  Scan         exclude docs; tests off (index is current)\n" in current.stdout, current.stdout)
        val json = runKscope("info", "--format", "json", "--workspace", root.toString())
        val scan = parseJson(json.stdout).getValue("settings").jsonObject.getValue("scan").jsonObject
        assertEquals("current", scan.getValue("indexState").jsonPrimitive.content)
        assertEquals("false", scan.getValue("tests").jsonPrimitive.content)
        assertEquals(listOf("docs"), scan.getValue("exclude").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun `info shows the analyzers that are off and whether the index was built without them`() =
        inScannedWorkspace { root ->
            val plain = runKscope("info", "--workspace", root.toString())
            assertTrue("  Analyzers " !in plain.stdout, plain.stdout)

            writeFile(root, "kscope.toml", "[analyzers]\naidl = false\nschemas = false\n")
            val outdated = runKscope("info", "--workspace", root.toString())
            val outdatedLine = "  Analyzers    off: aidl, schemas (index was built with other settings, scan again)\n"
            assertTrue(outdatedLine in outdated.stdout, outdated.stdout)

            assertEquals(0, runKscope("scan", "--workspace", root.toString()).statusCode)
            val current = runKscope("info", "--workspace", root.toString())
            assertTrue("  Analyzers    off: aidl, schemas (index is current)\n" in current.stdout, current.stdout)
            val json = runKscope("info", "--format", "json", "--workspace", root.toString())
            val analyzers = parseJson(json.stdout).getValue("settings").jsonObject.getValue("analyzers").jsonObject
            assertEquals("current", analyzers.getValue("indexState").jsonPrimitive.content)
            assertEquals("false", analyzers.getValue("aidl").jsonPrimitive.content)
            assertEquals("true", analyzers.getValue("gitHistory").jsonPrimitive.content)
        }

    @Test
    fun `a valid file is read by a scan, which then has nothing to redo`() = inScannedWorkspace { root ->
        writeFile(root, "kscope.toml", "[resources]\nworkers = 2\nworker-heap-mib = 512\n")

        val result = runKscope("scan", "--format", "json", "--workspace", root.toString())

        assertEquals(0, result.statusCode, result.output)
        assertEquals(0, parseJson(result.stdout).getValue("unitsProduced").jsonPrimitive.int)
    }

    @Test
    fun `a scan through a server is refused in the same way and the server keeps serving`() =
        inScannedWorkspace { root ->
            writeFile(root, "kscope.toml", invalid)
            val before = indexOf(root)

            withTestServer(root) { server ->
                val scan = server.query(listOf("scan"))
                val info = server.query(listOf("info"))

                assertEquals(1, scan.exitCode)
                assertTrue(firstError in scan.stderr && secondError in scan.stderr, scan.stderr)
                assertEquals(0, info.exitCode, info.stderr)
                assertTrue(firstError in info.stdout, info.stdout)
            }
            assertEquals(before, indexOf(root))
        }
}
