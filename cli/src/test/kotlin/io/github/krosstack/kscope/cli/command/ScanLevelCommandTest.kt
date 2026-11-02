package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.CommandExecution
import io.github.krosstack.kscope.cli.KscopeCommand
import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.parseWithExitCodes
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.runKscopeWith
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanLevelCommandTest {
    private fun markIndexExact(root: Path) {
        DriverManager.getConnection("jdbc:sqlite:${root.resolve(".kscope/index.db")}").use { connection ->
            connection.createStatement().use {
                assertEquals(
                    1,
                    it.executeUpdate(
                        "UPDATE producer SET name = 'kscope.build.gradle.tooling' " +
                            "WHERE name = 'kscope.build.gradle.static'",
                    ),
                )
            }
        }
    }

    private fun optionsOf(vararg arguments: String): OpenOptions {
        var seen: OpenOptions? = null
        val runner = ScanRunner { _, options, _, _ ->
            seen = options
            ScanResult(0, 0, 0, 0, DiagnosticSummary(0, 0, 0, emptyList()), null, emptyMap(), ".kscope", "resolved")
        }
        val command = KscopeCommand(execution = CommandExecution(scanRunner = runner))
        command.parseWithExitCodes(listOf("scan", "--quiet") + arguments)
        return checkNotNull(seen)
    }

    @Test
    fun `scan and info name the level of the index in text and json`() = inWorkspace { root ->
        val scan = runKscope("scan", "--workspace", root.toString())
        val scanJson = parseJson(runKscope("scan", "--workspace", root.toString(), "--format", "json").stdout)
        val infoJson = parseJson(runKscope("info", "--workspace", root.toString(), "--format", "json").stdout)
        val info = runKscope("info", "--workspace", root.toString())

        assertTrue(scan.stdout.contains("  Level       resolved\n"), scan.stdout)
        assertTrue(info.stdout.contains("  Level         resolved\n"), info.stdout)
        assertEquals("resolved", scanJson.getValue("indexLevel").jsonPrimitive.content)
        assertEquals("resolved", infoJson.getValue("indexLevel").jsonPrimitive.content)
        assertEquals(listOf("schemaVersion", "indexLevel"), scanJson.keys.take(2))
        assertEquals(listOf("schemaVersion", "indexLevel"), infoJson.keys.take(2))
    }

    @Test
    fun `every json result read from the index names its level right after the version`() = inScannedWorkspace { root ->
        val workspace = root.toString()
        val commands = listOf(
            listOf("analyze", "lib"),
            listOf("context", "lib"),
            listOf("symbol", "Lib"),
            listOf("impact", "lib/src/main/kotlin/Lib.kt"),
            listOf("related", "lib/src/main/kotlin/Lib.kt"),
            listOf("graph"),
        )
        for (command in commands) {
            val result = runKscopeWith(command + listOf("--workspace", workspace, "--format", "json"))
            assertEquals(0, result.statusCode, "$command: ${result.stderr}")
            val json = parseJson(result.stdout)
            assertEquals(listOf("schemaVersion", "indexLevel"), json.keys.take(2), "$command")
            assertEquals("resolved", json.getValue("indexLevel").jsonPrimitive.content, "$command")
        }
    }

    @Test
    fun `info of a workspace without an index fails as before`() = inWorkspace { root ->
        val result = runKscope("info", "--workspace", root.toString(), "--format", "json")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("kscope scan"), result.stderr)
    }

    @Test
    fun `an unknown level is refused and names the levels`() = inWorkspace { root ->
        val unknown = runKscope("scan", "--workspace", root.toString(), "--level", "deep")

        assertEquals(2, unknown.statusCode)
        assertTrue(unknown.stderr.contains("Unknown level"), unknown.stderr)
        assertTrue(unknown.stderr.contains("syntax, resolved, exact"), unknown.stderr)
        assertTrue(!root.resolve(".kscope").toFile().exists())
    }

    @Test
    fun `trust-build is the same as level exact and cannot be combined with another level`() = inWorkspace { root ->
        val trustBuild = optionsOf("--trust-build")
        val exact = optionsOf("--level", "exact")
        val both = optionsOf("--level", "exact", "--trust-build")
        val resolved = optionsOf("--level", "resolved")
        val plain = optionsOf()
        val conflict = runKscope("scan", "--workspace", root.toString(), "--level", "resolved", "--trust-build")

        assertTrue(trustBuild.trustBuild && exact.trustBuild && both.trustBuild)
        assertNull(trustBuild.level)
        assertNull(exact.level)
        assertEquals(ScanLevel.RESOLVED, resolved.level)
        assertTrue(!resolved.trustBuild)
        assertTrue(!plain.trustBuild)
        assertNull(plain.level)
        assertEquals(2, conflict.statusCode)
        assertTrue(conflict.stderr.contains("same as --level exact"), conflict.stderr)
    }

    @Test
    fun `a plain scan of an exact index in a workspace that is not trusted exits with 1 and changes nothing`() =
        inScannedWorkspace { root ->
            markIndexExact(root)
            val before = root.resolve(".kscope/index.db").toFile().readBytes()

            val result = runKscope("scan", "--workspace", root.toString())

            assertEquals(1, result.statusCode)
            assertTrue(result.stderr.startsWith("error: "), result.stderr)
            assertTrue(result.stderr.contains("kscope trust"), result.stderr)
            assertTrue(result.stderr.contains("kscope scan --level exact"), result.stderr)
            assertEquals("", result.stdout)
            assertTrue(before.contentEquals(root.resolve(".kscope/index.db").toFile().readBytes()))
        }

    @Test
    fun `a shallower level needs a rebuild and then replaces the exact index`() = inScannedWorkspace { root ->
        markIndexExact(root)
        val workspace = root.toString()

        val refused = runKscope("scan", "--workspace", workspace, "--level", "resolved")
        val rebuilt = runKscope("scan", "--workspace", workspace, "--level", "resolved", "--rebuild")
        val plain = runKscope("scan", "--workspace", workspace)

        assertEquals(1, refused.statusCode)
        assertTrue(refused.stderr.contains("--rebuild"), refused.stderr)
        assertEquals(0, rebuilt.statusCode)
        assertEquals(0, plain.statusCode)
        assertTrue(plain.stdout.contains("  Level       resolved\n"), plain.stdout)
    }

    @Test
    fun `a settings file that asks for exact scans at resolved with a warning in an untrusted workspace`() =
        inWorkspace { root ->
            root.resolve("kscope.toml").toFile().writeText("level = \"exact\"\n")

            val text = runKscope("scan", "--workspace", root.toString())
            val json = parseJson(runKscope("scan", "--workspace", root.toString(), "--format", "json").stdout)

            assertEquals(0, text.statusCode)
            assertTrue(text.stdout.contains("level.exact-needs-trust"), text.stdout)
            assertTrue(text.stdout.contains("  Level       resolved\n"), text.stdout)
            assertEquals("resolved", json.getValue("indexLevel").jsonPrimitive.content)
        }
}
