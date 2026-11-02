package io.github.krosstack.kscope.benchmark.agent

import io.github.krosstack.kscope.benchmark.gold.GoldTask
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentTaskPackagesTest {
    private val tasksDirectory: Path = Path.of(System.getProperty("kscope.gold")).resolve("tasks")
    private val gold = Json { ignoreUnknownKeys = true }

    private val tasks: List<AgentTask> = assertIs<TaskLoad.Loaded>(AgentTaskStore.load(tasksDirectory)).tasks

    private fun goldTask(id: String): GoldTask = Files.list(tasksDirectory).use { entries ->
        entries.filter { it.fileName.toString().startsWith("$id-") && it.fileName.toString().endsWith(".json") }
            .map { gold.decodeFromString<GoldTask>(Files.readString(it)) }.toList()
    }.single()

    @Test
    fun `at least six task packages load with distinct ids`() {
        assertTrue(tasks.size >= MINIMUM_TASKS)
        assertEquals(tasks.size, tasks.map { it.id }.toSet().size)
    }

    @Test
    fun `every statement extends the statement of its reference task and gold stays inside the reference gold`() {
        tasks.forEach { task ->
            val reference = goldTask(task.id)
            assertTrue(task.spec.statement.startsWith(reference.statement), task.id)
            val allowed = reference.mustEdit + reference.mustCreate + reference.mayEdit
            val tests = task.spec.mayEditFiles.toSet()
            assertTrue(task.spec.goldFiles.all { it in allowed }, "${task.id} gold files")
            assertTrue(allowed.all { it in task.spec.goldFiles || it in tests }, "${task.id} reference files")
        }
    }

    @Test
    fun `reference patches touch only gold or optional files and hidden tests exist`() {
        tasks.forEach { task ->
            val allowed = task.spec.goldFiles.toSet() + task.spec.mayEditFiles
            assertTrue(task.patchFiles().isNotEmpty(), task.id)
            assertTrue(task.patchFiles().all { it in allowed }, "${task.id}: ${task.patchFiles()}")
            assertTrue(task.hiddenFiles().isNotEmpty(), task.id)
        }
    }

    @Test
    fun `fail-to-pass tests are defined by the hidden files and never overlap pass-to-pass`() {
        tasks.forEach { task ->
            val hiddenText = task.hiddenFiles().joinToString("\n") { Files.readString(task.hiddenTests.resolve(it)) }
            task.spec.failToPass.forEach { id ->
                val method = id.substringAfter('#').substringBefore('[')
                assertTrue("fun $method(" in hiddenText, "${task.id}: $id")
            }
            assertTrue(task.spec.failToPass.intersect(task.spec.passToPass.toSet()).isEmpty(), task.id)
        }
    }

    @Test
    fun `packages hold no machine paths and no verification run inside the checked out gradle output`() {
        tasks.forEach { task ->
            val files = listOf(task.directory.resolve(AgentTask.SPEC), task.referencePatch) +
                task.hiddenFiles().map { task.hiddenTests.resolve(it) }
            files.forEach { file ->
                val text = Files.readString(file)
                assertFalse(Regex("[A-Za-z]:[\\\\/]|/Users/|/home/").containsMatchIn(text), "$file")
            }
        }
    }

    private companion object {
        const val MINIMUM_TASKS = 6
    }
}
