package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanCommandTest {
    @Test
    fun `scan reports what it indexed and exits with zero`() = inWorkspace { root ->
        val result = runKscope("scan", "--workspace", root.toString())
        assertEquals(0, result.statusCode)
        val produced = checkNotNull(Regex("""  Work units {2}(\d+) redone""").find(result.stdout)).groupValues[1]
        assertTrue(result.stdout.startsWith("Indexed 4 files in "), result.stdout)
        assertTrue(result.stdout.contains("  Work units  $produced redone, 0 reused, 0 removed\n"), result.stdout)
        assertTrue(result.stdout.contains("  Problems    0 errors, 0 warnings, 3 notes\n"), result.stdout)
        val second = runKscope("scan", "--workspace", root.toString())
        assertTrue(second.stdout.startsWith("The index is up to date: 4 files, nothing changed"), second.stdout)
        assertTrue(second.stdout.contains("  Work units  0 redone, $produced reused, 0 removed"), second.stdout)
    }

    @Test
    fun `scan can print its result as json`() = inWorkspace { root ->
        val result = runKscope("scan", "--workspace", root.toString(), "--format", "json")
        assertEquals(0, result.statusCode)
        val scan = parseJson(result.stdout)
        assertEquals(4, scan.getValue("artifacts").jsonPrimitive.int)
        assertTrue(scan.getValue("unitsProduced").jsonPrimitive.int > 0)
    }

    @Test
    fun `the common options are accepted before the command name as well`() = inWorkspace { root ->
        val result = runKscope("--workspace", root.toString(), "--format", "json", "scan")
        assertEquals(0, result.statusCode)
        assertEquals(4, parseJson(result.stdout).getValue("artifacts").jsonPrimitive.int)
    }

    @Test
    fun `scan with verify and rebuild passes on a consistent index`() = inWorkspace { root ->
        val result = runKscope("scan", "--workspace", root.toString(), "--rebuild", "--verify")
        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("The incremental index matches a clean rebuild."), result.stdout)
    }

    @Test
    fun `a verification mismatch exits with code three`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        DriverManager.getConnection("jdbc:sqlite:${root.resolve(".kscope/index.db")}").use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM artifact WHERE path = 'README.md'") }
        }
        val result = runKscope("scan", "--workspace", root.toString(), "--verify")
        assertEquals(3, result.statusCode)
        assertTrue(result.stdout.startsWith("Verification failed:"), result.stdout)
        assertTrue(result.stdout.contains("differs from a clean rebuild in 1 work unit"), result.stdout)
        assertTrue(result.stdout.contains("kscope.discovery"), result.stdout)
    }

    @Test
    fun `an unreadable index is reported as a failure`() = inWorkspace { root ->
        writeFile(root, ".kscope/index.db", "this is not a database")
        val result = runKscope("scan", "--workspace", root.toString())
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.startsWith("error: "))
        assertTrue(result.stderr.contains("kscope scan --rebuild"), result.stderr)
    }
}
