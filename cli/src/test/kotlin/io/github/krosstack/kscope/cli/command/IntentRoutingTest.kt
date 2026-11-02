package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.cli.resolve
import io.github.krosstack.kscope.cli.writeFile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class IntentRoutingTest {
    private fun <T> withWorkspace(block: (Path) -> T): T {
        val root = Files.createTempDirectory("kscope-routing")
        try {
            writeFile(root, "lib/src/main/kotlin/Greeter.kt", "class Greeter\n")
            writeFile(root, "notes/my notes.md", "# Notes\n")
            Files.createDirectories(root.resolve("lib/src/main/kotlin/package"))
            return block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun read(root: Path, text: String, requested: Reading? = null): Reading =
        IntentRouting.route(text, requested, root).reading

    @Test
    fun `one identifier in camel case or snake case is a name`() = withWorkspace { root ->
        listOf("TaskRepository", "taskRepository", "Task", "task_repository", "TASK_REPOSITORY", "_hidden").forEach {
            assertEquals(Reading.SYMBOL, read(root, it), it)
        }
    }

    @Test
    fun `Unicode identifiers keep the casing and underscore name heuristic`() = withWorkspace { root ->
        listOf("Überblick", "ÉcranView", "任务_仓库", "𐐀", "E\u0301cranView").forEach {
            assertEquals(Reading.SYMBOL, read(root, it), it)
        }
    }

    @Test
    fun `Unicode qualified identifiers are names`() = withWorkspace { root ->
        listOf("界面.视图", "écran.ÉcranView").forEach {
            assertEquals(Reading.SYMBOL, read(root, it), it)
        }
    }

    @Test
    fun `sentences and plain Unicode words remain tasks`() = withWorkspace { root ->
        listOf("add Überblick to the screen", "update ÉcranView", "修改 任务_仓库", "überblick", "视图").forEach {
            assertEquals(Reading.TASK, read(root, it), it)
        }
    }

    @Test
    fun `a qualified name is a name`() = withWorkspace { root ->
        listOf("sample.tasktide.data.db.TaskRepository", "TaskRepository.save", "data.db").forEach {
            assertEquals(Reading.SYMBOL, read(root, it), it)
        }
    }

    @Test
    fun `an existing file is a name with its workspace relative path`() = withWorkspace { root ->
        val routing = IntentRouting.route("lib/src/main/kotlin/Greeter.kt", null, root)
        val withLine = IntentRouting.route("lib/src/main/kotlin/Greeter.kt:7", null, root)
        val absolute = IntentRouting.route(root.resolve("lib/src/main/kotlin/Greeter.kt").toString(), null, root)
        val spaced = IntentRouting.route("notes/my notes.md", null, root)

        assertEquals(Reading.SYMBOL, routing.reading)
        assertEquals("lib/src/main/kotlin/Greeter.kt", routing.argument)
        assertEquals("lib/src/main/kotlin/Greeter.kt:7", withLine.argument)
        assertEquals("lib/src/main/kotlin/Greeter.kt", absolute.argument)
        assertEquals(Reading.SYMBOL, spaced.reading)
        assertEquals(false, routing.requested)
    }

    @Test
    fun `a plain word a sentence or a path that does not exist is a task`() = withWorkspace { root ->
        listOf(
            "reminder",
            "settings",
            "add a reminder to tasks",
            "Add a reminder",
            "TaskRepository please",
            "snake_case plus words",
            "e.g.",
            "Fix:",
            "lib/src/missing/Nothing.kt",
            "lib/src/main/kotlin/package",
            "",
            "   ",
            "123",
        ).forEach { assertEquals(Reading.TASK, read(root, it), "'$it'") }
    }

    @Test
    fun `a path outside the workspace is not read as a file`() = withWorkspace { root ->
        assertEquals(Reading.TASK, read(root, "../outside/File.kt"))
        assertEquals(Reading.TASK, read(root, Path.of("elsewhere", "File.kt").toAbsolutePath().toString()))
    }

    @Test
    fun `as overrides the guess in both directions and says that it was asked for`() = withWorkspace { root ->
        val task = IntentRouting.route("TaskRepository", Reading.TASK, root)
        val name = IntentRouting.route("add a reminder", Reading.SYMBOL, root)

        assertEquals(Reading.TASK, task.reading)
        assertEquals(Reading.SYMBOL, name.reading)
        assertEquals(true, task.requested && name.requested)
        assertEquals("add a reminder", name.argument)
    }
}
