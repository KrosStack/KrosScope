package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.TemporaryTree
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.localization.BenchmarkMethod
import io.github.krosstack.kscope.benchmark.runCommand
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceTasksRunTest {
    private val taskIds = listOf("W01", "W02", "W03", "W04", "W05", "W06")

    @Test
    fun `every task has a result row for every method and budget`() {
        result.tasks.forEach { task ->
            val scores = task.scores
            assertEquals(emptyList(), task.missingGoldFiles, task.id)
            assertEquals(BenchmarkMethod.entries, scores.localization.map { it.method }, task.id)
            assertEquals(
                ContextBenchmarkOptions.DEFAULT_METHODS.size * result.budgets.size,
                scores.context.size,
                task.id,
            )
            assertEquals(ContextBenchmarkOptions.DEFAULT_METHODS.toSet(), scores.context.map { it.method }.toSet())
        }
        assertEquals(taskIds, result.tasks.map { it.id })
        assertEquals(ContextBenchmarkBudgets.values.map { it.value }, result.budgets)
    }

    @Test
    fun `every metric lies between 0 and 1`() {
        val scores = result.tasks.map { it.scores } + result.groups.map { it.scores }
        val values = scores.flatMap { score ->
            score.localization.flatMap { listOf(it.recall5, it.recall10, it.ndcg10) } +
                score.context.flatMap { listOf(it.goldRecall, it.tokenPrecision) }
        }

        assertTrue(values.isNotEmpty() && values.all { it in 0.0..1.0 }, values.filterNot { it in 0.0..1.0 }.toString())
    }

    @Test
    fun `the groups are all tasks, the named tasks and the tasks that are not named`() {
        assertEquals(listOf("All tasks", "Named", "Not named"), result.groups.map { it.group })
        val expected = listOf(taskIds, listOf("W01", "W03", "W05"), listOf("W02", "W04", "W06"))

        assertEquals(expected, result.groups.map { it.tasks })
    }

    @Test
    fun `the command writes the result and the report and exits with 0`() {
        val out = Files.createTempDirectory("kscope-workspace-tasks-command")
        try {
            val printed = mutableListOf<String>()

            val code = runCommand(listOf("workspace-tasks", "--out", out.toString(), "--budgets", "2000"), printed::add)

            assertEquals(0, code)
            assertTrue(Files.readString(out.resolve("workspace-tasks.json")).contains("\"id\": \"W06\""))
            assertEquals(printed.single(), Files.readString(out.resolve("workspace-tasks.md")))
            assertTrue(printed.single().contains("| 3 | Not named |"))
        } finally {
            TemporaryTree.delete(out)
        }
    }

    @Test
    fun `arguments that are not flags with a value or budgets that are not standard print the usage`() {
        val invalid = listOf(
            listOf("workspace-tasks", "--out"),
            listOf("workspace-tasks", "--level", "x"),
            listOf("workspace-tasks", "--budgets", "100"),
        )
        invalid.forEach { args ->
            val printed = mutableListOf<String>()

            assertEquals(2, runCommand(args, printed::add), args.joinToString(" "))
            assertTrue(printed.single().startsWith("usage: workspace-tasks"))
        }
    }

    private companion object {
        val result: WorkspaceTasksResult by lazy {
            WorkspaceTasksRun(
                Path.of(checkNotNull(System.getProperty("kscope.workspaceFixture"))),
                Path.of(checkNotNull(System.getProperty("kscope.workspaceGold"))),
                ContextBenchmarkBudgets.values,
            ).run()
        }
    }
}
