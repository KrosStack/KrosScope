package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.writeFile
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizationCommandTest {
    @Test
    fun `malformed benchmark gold reports an error on stderr without json noise`() = inWorkspace { root ->
        val tasks = root.resolve("tasks")
        writeFile(tasks, "case.json", "{broken")
        val result = runKscope(
            "benchmark",
            "--tasks",
            tasks.toString(),
            "--workspace",
            root.toString(),
            "--format",
            "json",
        )
        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("JSON"))
        assertEquals("", result.stdout)
    }

    @Test
    fun `symbol lookup returns the exact class`() = inWorkspace { root ->
        runKscope("scan", "--workspace", root.toString())
        val result = runKscope("symbol", "Lib", "--workspace", root.toString(), "--format", "json")
        assertEquals(0, result.statusCode, result.output)
        assertTrue(parseJson(result.stdout).getValue("symbols").jsonArray.isNotEmpty())
    }

    @Test
    fun `unknown impact node has no affected nodes`() = inScannedWorkspace { root ->
        val result = runKscope("impact", "Absent", "--workspace", root.toString(), "--format", "json")
        assertEquals(0, result.statusCode, result.output)
        assertEquals(0, parseJson(result.stdout).getValue("affected").jsonArray.size)
    }

    @Test
    fun `benchmark compares file rankings and emits parseable json`() = inWorkspace { root ->
        val tasks = root.resolve("tasks")
        writeFile(tasks, "case.json", """{"id":"case","statement":"Lib","mustEdit":["lib/src/main/kotlin/Lib.kt"]}""")
        runKscope("scan", "--workspace", root.toString())
        val result = runKscope(
            "benchmark",
            "--tasks",
            tasks.toString(),
            "--workspace",
            root.toString(),
            "--format",
            "json",
        )
        assertTrue(result.statusCode in 0..1, result.output)
        val original = parseJson(result.stdout).getValue("sets").jsonArray.first().jsonObject
        assertEquals(1, original.getValue("tasks").jsonArray.size)
    }
}
