package io.github.krosstack.kscope.benchmark

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceConformanceCommandTest {
    @Test
    fun `arguments that are not flags with a value print the usage`() {
        val invalid = listOf(listOf("workspace-conformance", "--out"), listOf("workspace-conformance", "--level", "x"))
        invalid.forEach { args ->
            val printed = mutableListOf<String>()

            assertEquals(2, runCommand(args, printed::add), args.joinToString(" "))
            assertTrue(printed.single().startsWith("usage: workspace-conformance"))
        }
    }

    @Test
    fun `the command writes the result and the table and exits with 0 when every class is found`() {
        val out = Files.createTempDirectory("kscope-workspace-command")
        try {
            val printed = mutableListOf<String>()

            assertEquals(0, runCommand(listOf("workspace-conformance", "--out", out.toString()), printed::add))
            assertTrue(Files.readString(out.resolve("workspace-links.md")).contains("| `spec_input_to_spec` |"))
            val json = Files.readString(out.resolve("workspace-links.json"))
            assertTrue(json.contains("\"name\": \"jni_external_to_native\""))
            assertTrue(printed.single().contains("Gate: met."))
        } finally {
            TemporaryTree.delete(out)
        }
    }
}
