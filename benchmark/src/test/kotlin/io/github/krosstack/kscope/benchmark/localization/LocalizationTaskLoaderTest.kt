package io.github.krosstack.kscope.benchmark.localization

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalizationTaskLoaderTest {
    @Test
    fun `held out statements replace only statements and must pair exactly with original ids`() {
        val tasks = listOf(
            LocalizationTask("b", "second", listOf("source-b")),
            LocalizationTask("a", "first", listOf("source-a")),
        )
        val statements = listOf(
            LocalizationStatement("a", "paraphrase a"),
            LocalizationStatement("b", "paraphrase b"),
        )
        val paired = LocalizationBenchmark.pairStatements(tasks, statements)
        assertEquals(
            listOf(tasks[1].copy(statement = "paraphrase a"), tasks[0].copy(statement = "paraphrase b")),
            paired,
        )
        assertFailsWith<IllegalArgumentException> { LocalizationBenchmark.pairStatements(tasks, statements.take(1)) }
        assertFailsWith<IllegalArgumentException> {
            LocalizationBenchmark.pairStatements(tasks, statements + statements)
        }
        assertFailsWith<IllegalArgumentException> {
            LocalizationBenchmark.pairStatements(tasks, statements + LocalizationStatement("extra", "additional"))
        }
        assertFailsWith<IllegalArgumentException> {
            LocalizationBenchmark.pairStatements(tasks, listOf(statements[0], LocalizationStatement("b", "")))
        }
    }

    @Test
    fun `task loader reads gold independently of directory entries and allows extra metadata`() {
        val directory = Files.createTempDirectory("kscope-localization-tasks")
        try {
            Files.createDirectory(directory.resolve("nested"))
            val task = LocalizationTask("sample", "change behavior", listOf("source"), listOf("context"))
            val json = Json.encodeToString(task).dropLast(1) + ",\"category\":\"boundary\"}"
            Files.writeString(directory.resolve("task.json"), json)
            assertEquals(listOf(task), LocalizationBenchmark.loadTasks(directory))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
