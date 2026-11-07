package io.github.krosstack.kscope.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HelpTextTest {
    private val root = KscopeCommand().also { it.resetContext() }
    private val commands = root.registeredSubcommands()

    @Test
    fun `every command has a description and an example`() {
        val names = commands.map { it.commandName }
        val expected = listOf(
            "scan", "update", "context", "analyze", "info", "symbol", "impact", "related", "graph", "trust",
            "workspace", "doctor", "benchmark", "components", "serve", "service", "mcp", "skill",
        )
        assertEquals(expected, names)
        (listOf(root) + commands).forEach { command ->
            val context = command.currentContext
            assertTrue(command.help(context).isNotBlank(), "${command.commandName} has no description")
            assertTrue(command.helpEpilog(context).contains("Example"), "${command.commandName} has no example")
        }
    }

    @Test
    fun `every option and argument of every command has help text`() {
        (listOf(root) + commands).forEach { command ->
            command.registeredOptions().forEach { option ->
                val help = option.optionHelp(command.currentContext)
                assertTrue(help.isNotBlank(), "${command.commandName} ${option.names} has no help text")
            }
            command.registeredArguments().forEach { argument ->
                val help = argument.getArgumentHelp(command.currentContext)
                assertTrue(help.isNotBlank(), "${command.commandName} ${argument.name} has no help text")
            }
        }
    }

    @Test
    fun `the top level help groups the commands and hides the update alias`() {
        val help = runKscope("--help").stdout
        val groups =
            listOf("Build the index:", "Ask about a task:", "Inspect the code:", "Keep it ready:", "Diagnose:")

        assertEquals(groups, groups.sortedBy { help.indexOf(it) }, help)
        groups.forEach { assertTrue(help.contains(it), "no group $it") }
        commands.map { it.commandName }.filter { it != "update" }.forEach {
            assertTrue(Regex("""(?m)^ {2}$it +\S""").containsMatchIn(help), "$it is not listed in:\n$help")
        }
        assertFalse(Regex("""(?m)^ {2}update\b""").containsMatchIn(help), "update is listed in:\n$help")
    }

    @Test
    fun `update still works and says that it is an alias of scan`() = inWorkspace { root ->
        val help = runKscope("update", "--help").stdout
        assertTrue(help.contains("Compatibility alias of `scan`"), help)
        assertTrue(help.contains("--rebuild") && help.contains("--verify") && help.contains("--trust-build"), help)

        val updated = runKscope("update", "--workspace", root.toString(), "--format", "json")
        assertEquals(0, updated.statusCode, updated.output)
        assertEquals(4, parseJson(updated.stdout).getValue("artifacts").toString().toInt())
    }

    @Test
    fun `scan is documented as the one command that indexes`() {
        val help = runKscope("scan", "--help").stdout

        assertTrue(help.contains("Builds the index of the workspace, or brings it up to date."), help)
        assertTrue(help.contains("--rebuild"), help)
        assertTrue(help.contains("Examples:") && help.contains("kscope scan --rebuild"), help)
        assertFalse(help.contains("alias"), help)
    }

    @Test
    fun `each command help ends with its examples`() {
        commands.forEach { command ->
            val help = runKscope(command.commandName, "--help").stdout.trimEnd()
            val lastBlock = help.substringAfterLast("\n\n")

            val name = command.commandName
            assertTrue(lastBlock.startsWith("Example"), "$name help does not end with examples:\n$help")
            assertTrue(lastBlock.contains("kscope ${command.commandName}"), "${command.commandName}:\n$lastBlock")
        }
    }
}
