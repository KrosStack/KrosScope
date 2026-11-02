package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.runContextBenchmarkCommand
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartialBudgetRunTest {
    private fun write(root: Path, relative: String, text: String) {
        val file = root.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }

    @Test
    fun `a run restricted with budgets states that it is not a Tier 1 evaluation and does not pass`() {
        val root = Files.createTempDirectory("kscope-context-benchmark")
        try {
            write(root, "workspace/settings.gradle.kts", "include(\":lib\")\n")
            write(root, "workspace/lib/build.gradle.kts", "")
            write(root, "workspace/lib/src/main/kotlin/Lib.kt", "package sample\n\nclass Lib {\n    fun run() = 1\n}\n")
            write(
                root,
                "gold/tasks/case.json",
                """{"id":"case","statement":"Change what Lib runs","mustEdit":["lib/src/main/kotlin/Lib.kt"]}""",
            )
            val printed = mutableListOf<String>()
            val code = runContextBenchmarkCommand(
                listOf(
                    "context-benchmark", "--mode", "default", "--out", root.resolve("out").toString(),
                    "--workspace", root.resolve("workspace").toString(), "--gold", root.resolve("gold").toString(),
                    "--original-only", "--budgets", "2000",
                ),
                printed::add,
            )

            val report = printed.joinToString("\n")
            assertTrue(report.contains("Tier 1: NOT EVALUATED."), report)
            assertTrue(report.contains("A partial-budget run is not a Tier 1 evaluation"), report)
            assertFalse(report.lineSequence().any { it.startsWith("Tier 1: PASS") }, report)
            assertEquals(1, code, "a partial-budget run does not pass Tier 1")
            val json = Json.parseToJsonElement(Files.readString(root.resolve("out/context-benchmark.json"))).jsonObject
            assertEquals("false", json.getValue("tier1Passed").jsonPrimitive.content)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
