package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.workspace.tasks.WorkspaceTasksReport
import io.github.krosstack.kscope.benchmark.workspace.tasks.WorkspaceTasksRun
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.context.TokenCount
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

private const val USAGE = "usage: workspace-tasks [--out <directory>] [--workspace <directory>] " +
    "[--gold <directory>] [--budgets 2000,4000,8000,16000]"
private const val DEFAULT_FIXTURE = "fixtures/workspace-of-repositories"
private const val DEFAULT_GOLD = "benchmark/workspace"
private const val USAGE_ERROR = 2

private val prettyJson = Json(from = KscopeJson) {
    prettyPrint = true
}

/** The options of the `workspace-tasks` command: where to write the result, the fixture and gold, and the budgets. */
internal class WorkspaceTasksOptions(
    val out: Path?,
    val fixture: Path,
    val gold: Path,
    val budgets: List<TokenCount>,
) {
    companion object {
        private val FLAGS = setOf("--out", "--workspace", "--gold", "--budgets")

        fun parse(args: List<String>): WorkspaceTasksOptions? {
            val pairs = args.drop(1).chunked(2)
            val flags = pairs.associate { it.first() to it.last() }
            val budgetText = flags["--budgets"]
            val budgets = budgetText?.let(ContextBenchmarkBudgets::parse)
                ?: ContextBenchmarkBudgets.values.takeIf { budgetText == null }
            val fixture = flags["--workspace"] ?: System.getProperty("kscope.workspaceFixture") ?: DEFAULT_FIXTURE
            val gold = flags["--gold"] ?: System.getProperty("kscope.workspaceGold") ?: DEFAULT_GOLD
            val valid = pairs.all { it.size == 2 && it.first() in FLAGS }
            return if (!valid || budgets == null) {
                null
            } else {
                WorkspaceTasksOptions(
                    out = flags["--out"]?.let(Path::of),
                    fixture = Path.of(fixture).toAbsolutePath().normalize(),
                    gold = Path.of(gold).toAbsolutePath().normalize(),
                    budgets = budgets,
                )
            }
        }
    }
}

/** Runs the command; the exit code is 0 whenever the run completes, because the results carry no gate. */
internal fun runWorkspaceTasksCommand(args: List<String>, print: (String) -> Unit): Int {
    val options = WorkspaceTasksOptions.parse(args)
    if (options == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    val result = WorkspaceTasksRun(options.fixture, options.gold, options.budgets).run()
    val report = WorkspaceTasksReport(result).render()
    options.out?.let { out ->
        Files.createDirectories(out)
        Files.writeString(out.resolve("workspace-tasks.json"), prettyJson.encodeToString(result) + "\n")
        Files.writeString(out.resolve("workspace-tasks.md"), report)
    }
    print(report)
    return 0
}
