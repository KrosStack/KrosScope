package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.inScannedWorkspace
import io.github.krosstack.kscope.cli.inWorkspace
import io.github.krosstack.kscope.cli.parseJson
import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.runKscope
import io.github.krosstack.kscope.cli.taskInput
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class TaskFileTest {
    @Test
    fun `analyze and context keep utf8 task file text including non bmp characters`() = inScannedWorkspace { root ->
        val task = "Réparer 测试 🚀"
        val path = root.resolve("task.txt")
        Files.writeString(path, task, Charsets.UTF_8)
        for (command in listOf("analyze", "context")) {
            val result = runKscope(
                command,
                "--task-file",
                path.toString(),
                "--workspace",
                root.toString(),
                "--format",
                "json",
            )
            assertEquals(0, result.statusCode, result.output)
            val json = parseJson(result.stdout)
            val actual = if (command == "analyze") {
                json.getValue("query").jsonObject.getValue("text")
            } else {
                json.getValue("task")
            }
            assertEquals(task, actual.jsonPrimitive.content)
        }
    }

    @Test
    fun `task file and positional task are mutually exclusive`() = inWorkspace { root ->
        val path = root.resolve("task.txt")
        Files.writeString(path, "task", Charsets.UTF_8)
        for (command in listOf("analyze", "context")) {
            val result = runKscope(
                command,
                "positional",
                "--task-file",
                path.toString(),
                "--workspace",
                root.toString(),
            )

            assertEquals(2, result.statusCode)
            assertEquals("", result.stdout)
        }
    }

    @Test
    fun `task files accept a bom and reject malformed utf8`() = inWorkspace { root ->
        val path = root.resolve("task.txt")
        Files.writeString(path, "\uFEFFcaf\u00e9", Charsets.UTF_8)
        assertEquals("caf\u00e9", taskInput(null, path))
        Files.write(path, byteArrayOf(0xC3.toByte(), 0x28))
        val result = runKscope("context", "--task-file", path.toString(), "--workspace", root.toString())

        assertEquals(1, result.statusCode)
        assertEquals("", result.stdout)
    }
}
