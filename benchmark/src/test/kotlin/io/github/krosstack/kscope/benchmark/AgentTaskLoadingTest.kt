package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.agent.AgentTask
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTaskLoadingTest {
    @TempDir
    lateinit var directory: Path

    private val workingDirectory: Path = Path.of("").toAbsolutePath()
    private val gold: Path = Path.of(checkNotNull(System.getProperty("kscope.gold")))
    private val relativeGold: Path = workingDirectory.relativize(gold)

    private fun load(vararg args: String): List<AgentTask> {
        val printed = mutableListOf<String>()
        val flags = checkNotNull(AgentFlags.parse(args.toList(), setOf("--tasks"), emptySet()))
        return checkNotNull(loadTasks(flags, printed::add)) { printed.toString() }
    }

    private fun assertPackagesAreAbsoluteBelow(tasks: List<AgentTask>, directory: Path) {
        assertTrue(tasks.isNotEmpty())
        tasks.forEach { task ->
            assertEquals(directory.resolve(task.id), task.directory)
            assertTrue(task.referencePatch.isAbsolute && Files.isRegularFile(task.referencePatch), task.id)
            assertTrue(task.hiddenTests.isAbsolute && Files.isDirectory(task.hiddenTests), task.id)
        }
    }

    @Test
    fun `tasks loaded through a relative tasks flag have absolute paths`() {
        val relativeTasks = relativeGold.resolve("tasks")

        val tasks = load("--tasks", relativeTasks.toString())

        assertPackagesAreAbsoluteBelow(tasks, workingDirectory.resolve(relativeTasks).normalize())
    }

    @Test
    fun `tasks loaded through a relative gold property have absolute paths`() {
        val tasks = ScopedSystemProperties.with(mapOf("kscope.gold" to relativeGold.toString())) { load() }

        assertPackagesAreAbsoluteBelow(tasks, workingDirectory.resolve(relativeGold).resolve("tasks").normalize())
    }

    @Test
    fun `a task directory without a task package is refused instead of loading no task`() {
        val printed = mutableListOf<String>()
        val arguments = listOf("--tasks", directory.toString())
        val flags = checkNotNull(AgentFlags.parse(arguments, setOf("--tasks"), emptySet()))

        assertNull(loadTasks(flags, printed::add))
        assertEquals(listOf("The task directory holds no task package."), printed)
    }
}
