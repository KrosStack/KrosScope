package io.github.krosstack.kscope.cli

import com.github.ajalt.clikt.core.CoreCliktCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommonOptionsHelpTest {
    private val root = KscopeCommand().also { it.resetContext() }
    private val dotWord = Regex("""\bdot\b""")

    @Test
    fun `root and analyze list ml controls and every command still accepts no ml`() = inWorkspace { workspace ->
        (listOf(emptyList<String>()) + allCommandPaths()).forEach { path ->
            val expected = path.isEmpty() || path == listOf("analyze")
            assertEquals(expected, helpOf(path).contains("--no-ml"), path.joinToString(" "))
            assertEquals(expected, helpOf(path).lists("--ml"), path.joinToString(" "))
        }

        val before = runKscope("--no-ml", "info", "--structure", "--workspace", workspace.toString())
        val after = runKscope("info", "--structure", "--no-ml", "--workspace", workspace.toString())

        assertEquals(0, before.statusCode, before.output)
        assertEquals(0, after.statusCode, after.output)
    }

    @Test
    fun `detail and sections are listed by analyze symbol and related only`() {
        val presented = listOf(listOf("analyze"), listOf("symbol"), listOf("related"))

        allCommandPaths().forEach { path ->
            val help = helpOf(path)
            val expected = path in presented
            assertEquals(expected, help.lists("--detail"), "${path.joinToString(" ")} and --detail")
            assertEquals(expected, help.lists("--sections"), "${path.joinToString(" ")} and --sections")
        }
    }

    @Test
    fun `dot is advertised by graph and by no other command except the root`() {
        val advertising = allCommandPaths().filter { dotWord.containsMatchIn(helpOf(it)) }

        assertEquals(listOf(listOf("graph")), advertising)
        assertTrue(dotWord.containsMatchIn(helpOf(emptyList())))
    }

    @Test
    fun `an option that a command hides still parses`() = inWorkspace { workspace ->
        val root = workspace.toString()
        val quiet = runKscope("info", "--structure", "--progress", "never", "-q", "--workspace", root)
        val status = runKscope("serve", "--status", "--format", "json", "--workspace", root)
        val scan = runKscope("scan", "--no-server", "--workspace", root)
        val skill = runKscope("skill", "show", "--format", "json", "--workspace", root)

        assertEquals(0, quiet.statusCode, quiet.output)
        assertEquals(1, status.statusCode, status.output)
        assertTrue(status.stdout.contains("No server runs"), status.output)
        assertEquals(0, scan.statusCode, scan.output)
        assertEquals(0, skill.statusCode, skill.output)
    }

    @Test
    fun `a hidden option that the command refuses is still refused`() = inWorkspace { workspace ->
        val root = workspace.toString()
        val dot = runKscope("info", "--format", "dot", "--workspace", root)
        val detail = runKscope("context", "a task", "--detail", "brief", "--workspace", root)

        assertEquals(2, dot.statusCode, dot.output)
        assertTrue(dot.stderr.contains("--format dot is available for the graph command only."), dot.stderr)
        assertEquals(2, detail.statusCode, detail.output)
        assertTrue(detail.stderr.contains("--detail and --sections are available for analyze"), detail.stderr)
    }

    @Test
    fun `the options of a command come before the common options`() {
        val help = helpOf(listOf("scan"))

        assertEquals(
            listOf("Options:", "Common options:"),
            Regex("(?m)^(Options|Common options):").findAll(help).map { it.value }.toList(),
        )
        assertTrue(help.indexOf("--rebuild") < help.indexOf("Common options:"), help)
        assertTrue(help.indexOf("--workspace") > help.indexOf("Common options:"), help)
        assertFalse(helpOf(listOf("skill", "show")).contains("Common options:"))
    }

    @Test
    fun `each command lists only the common options that it honours`() {
        val query = listOf("--workspace", "--format", "--progress", "--quiet", "--no-server")
        val expected = mapOf(
            listOf("scan") to listOf("--workspace", "--format", "--progress", "--quiet"),
            listOf("update") to listOf("--workspace", "--format", "--progress", "--quiet"),
            listOf("context") to query,
            listOf("impact") to query,
            listOf("graph") to query,
            listOf("analyze") to query + listOf("--detail", "--sections", "--no-ml", "--ml", "--stage"),
            listOf("symbol") to query + listOf("--detail", "--sections"),
            listOf("related") to query + listOf("--detail", "--sections"),
            listOf("info") to listOf("--workspace", "--format", "--no-server"),
            listOf("doctor") to listOf("--workspace", "--format"),
            listOf("benchmark") to listOf("--workspace", "--format"),
            listOf("mcp") to listOf("--workspace", "--no-server"),
            listOf("service") to listOf("--workspace"),
            listOf("serve") to listOf("--workspace"),
            listOf("trust") to listOf("--workspace"),
            listOf("components") to listOf("--workspace", "--format"),
            listOf("components", "list") to listOf("--workspace", "--format"),
            listOf("components", "status") to listOf("--format"),
        )

        allCommandPaths().forEach { path ->
            val honoured = expected[path] ?: if (path.first() == "workspace") listOf("--workspace") else emptyList()
            val listed = commonOptionsOf(helpOf(path))
            assertEquals(honoured.sorted(), listed.sorted(), "common options of ${path.joinToString(" ")}")
        }
    }

    private fun commonOptionsOf(help: String): List<String> {
        val section = help.substringAfter("Common options:", "").substringBefore("\n\n")
        return Regex("""(?m)^ {2}(?:-\w, )?(--[a-z-]+)""").findAll(section).map { it.groupValues[1] }.toList()
    }

    private fun helpOf(path: List<String>): String = runKscopeWith(path + "--help").stdout

    private fun allCommandPaths(
        command: CoreCliktCommand = root,
        prefix: List<String> = emptyList(),
    ): List<List<String>> = command.registeredSubcommands().flatMap { child ->
        val path = prefix + child.commandName
        listOf(path) + allCommandPaths(child, path)
    }

    private fun String.lists(option: String): Boolean =
        Regex("""(?m)^ +(-\w, )?""" + Regex.escape(option) + """\b""").containsMatchIn(this)
}
