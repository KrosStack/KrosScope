package io.github.krosstack.kscope.cli.status

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.lookup.inNestedWorkspace
import io.github.krosstack.kscope.cli.lookup.runIn
import io.github.krosstack.kscope.cli.lookup.slashed
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.runKscope
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatusIndexKindsTest {
    @Test
    fun `an index of another version is named as that, in the folder where it is, and is left byte for byte`() =
        inNestedWorkspace { nested ->
            val inner = nested.root.resolve("lib/src")
            Files.createDirectories(inner)
            inner.resolve("Notes.md").toFile().writeText("# Notes\n")
            check(runKscope("scan", "--quiet", "--level", "syntax", "--workspace", inner.toString()).statusCode == 0)
            setUserVersion(inner.resolve(".kscope/index.db"), 1)
            val older = Files.readAllBytes(inner.resolve(".kscope/index.db")).toList()
            val current = Files.readAllBytes(nested.root.resolve(".kscope/index.db")).toList()

            val fromOlder = runIn(inner, nested.base, listOf("status"))
            val fromCurrent = runStatus(nested.root)

            assertEquals(1, fromOlder.statusCode)
            assertTrue(fromOlder.stdout.contains("is of another format (version 1;"), fromOlder.stdout)
            assertTrue(fromOlder.stdout.contains("${slashed(inner)}/.kscope"), fromOlder.stdout)
            assertEquals(0, fromCurrent.statusCode)
            assertEquals(older, Files.readAllBytes(inner.resolve(".kscope/index.db")).toList())
            assertEquals(current, Files.readAllBytes(nested.root.resolve(".kscope/index.db")).toList())
        }

    @Test
    fun `a file that is not a database says that it cannot be read and exits with 1`() = inWorkspace { root ->
        Files.createDirectories(root.resolve(".kscope"))
        Files.writeString(root.resolve(".kscope/index.db"), "not a database")

        val result = runStatus(root)

        assertEquals(1, result.statusCode)
        val opening = "kscope: the index in ${slashed(root)}/.kscope cannot be read ("
        assertTrue(result.stdout.startsWith(opening), result.stdout)
        assertTrue(result.stdout.trimEnd().endsWith("run kscope scan --rebuild"), result.stdout)
    }

    @Test
    fun `status and info give the same level on a syntax, a resolved and an exact index`() {
        listOf("syntax" to false, "resolved" to false, "resolved" to true).forEach { (scanLevel, exact) ->
            inWorkspace { root ->
                val scan = runKscope("scan", "--quiet", "--level", scanLevel, "--workspace", root.toString())
                check(scan.statusCode == 0)
                if (exact) markIndexExact(root)

                val fromStatus = levelIn(parseJson(runStatus(root, "--format", "json").stdout))
                val info = runKscope("info", "--format", "json", "--workspace", root.toString())
                val fromInfo = levelIn(parseJson(info.stdout))

                assertEquals(fromInfo, fromStatus, "$scanLevel exact=$exact")
                assertEquals(if (exact) "exact" else scanLevel, fromStatus)
            }
        }
    }

    @Test
    fun `it writes nothing, and it never starts a server`() = inScannedWorkspace { root ->
        val before = snapshot(root)

        runStatus(root)
        runStatus(root, "--format", "json")

        assertEquals(before, snapshot(root))
        assertTrue(Files.notExists(root.resolve(".kscope/serve.json")))
        assertTrue(before.keys.any { it.endsWith("scan.json") })
    }
}
