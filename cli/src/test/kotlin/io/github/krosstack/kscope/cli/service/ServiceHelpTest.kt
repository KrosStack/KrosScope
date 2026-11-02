package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.runKscope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceHelpTest {
    @Test
    fun `the service shares the serve group and lists its subcommands`() {
        val root = runKscope("--help").stdout
        val group = root.substringAfter("Keep it ready:").substringBefore("Diagnose:")
        assertTrue(Regex("(?m)^ {2}service +\\S").containsMatchIn(group), group)

        val help = runKscope("service", "--help")

        assertEquals(0, help.statusCode, help.output)
        for (name in listOf("run", "start", "stop", "status", "add", "remove", "install", "uninstall")) {
            assertTrue(Regex("(?m)^ {2}$name +\\S").containsMatchIn(help.stdout), "$name is not listed in:\n$help")
        }
        assertTrue(help.stdout.trimEnd().substringAfterLast("\n\n").startsWith("Examples:"))
    }

    @Test
    fun `add and remove document their workspace option`() {
        for (name in listOf("add", "remove")) {
            val help = runKscope("service", name, "--help")

            assertEquals(0, help.statusCode, help.output)
            assertTrue(help.stdout.contains("--workspace"), help.stdout)
            assertTrue(help.stdout.trimEnd().substringAfterLast("\n\n").startsWith("Example"), help.stdout)
        }
    }
}
