package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.ContextProgressEvent
import io.github.krosstack.kscope.benchmark.context.ContextReport
import io.github.krosstack.kscope.benchmark.context.FileContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files

private const val USAGE = "usage: context-benchmark --mode default --out <directory> " +
    "[--workspace <directory>] [--gold <directory>] [--held-out <file-or-directory> | --original-only] " +
    "[--checkpoint-key <freeze-sha>] [--budgets 2000,4000,8000,16000]"
private const val USAGE_ERROR = 2

internal class ContextBenchmarkArguments(
    val options: LocalizationOptions,
    val checkpointKey: String?,
    val budgets: List<TokenCount>,
) {
    companion object {
        fun parse(args: List<String>): ContextBenchmarkArguments? {
            val checkpoint = FlagArgument.parse(args, "--checkpoint-key")
            val budgetFlag = checkpoint?.let { FlagArgument.parse(it.remaining, "--budgets") }
            val budgets = budgetFlag?.value?.let(ContextBenchmarkBudgets::parse)
            val options = budgetFlag?.takeIf { it.value == null || budgets != null }
                ?.let { LocalizationOptions.parse(listOf("localization") + it.remaining.drop(1)) }
            return if (options == null || args.firstOrNull() != "context-benchmark") {
                null
            } else {
                ContextBenchmarkArguments(options, checkpoint.value, budgets ?: ContextBenchmarkBudgets.values)
            }
        }
    }
}

internal fun runContextBenchmarkCommand(args: List<String>, print: (String) -> Unit): Int {
    val arguments = ContextBenchmarkArguments.parse(args)
    if (arguments == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    val paths = arguments.options.conformance
    val tasks = LocalizationBenchmark.loadTasks(paths.gold.resolve("tasks")).map(ContextBenchmarkTask::fromLocalization)
    val heldOut = arguments.options.heldOut?.let(LocalizationBenchmark::loadStatements)
    Files.createDirectories(paths.out)
    val openOptions = measuredOpenOptions(paths.out.resolve("context-index"))
    val result = KrosScope.open(paths.workspace, openOptions).use { scope ->
        val scan = scope.scan()
        check(scan.diagnostics.errors == 0) { "Scan failed: ${scan.diagnostics.errors} errors" }
        val checkpoints = FileContextTaskCheckpoints(
            paths.out.resolve("context-checkpoints"),
            arguments.checkpointKey ?: "unfrozen",
            resume = arguments.checkpointKey != null,
        )
        val measurement = ContextBenchmarkOptions(
            budgets = arguments.budgets,
            progress = ::printProgress,
            checkpoints = checkpoints,
        )
        ContextBenchmark.run(scope, paths.workspace, tasks, heldOut, measurement)
    }
    val markdown = ContextReport.markdown(result)
    Files.writeString(paths.out.resolve("context-benchmark.json"), BenchmarkJson.encode(result) + "\n")
    Files.writeString(paths.out.resolve("context-benchmark.md"), markdown)
    print(markdown)
    return if (result.tier1Passed) 0 else 1
}

private fun printProgress(event: ContextProgressEvent) {
    System.err.println(
        "context-progress boundary=${event.boundary} set=${event.set} " +
            "task=${event.taskId ?: "-"} budget=${event.budget?.value ?: "-"} method=${event.method ?: "-"}",
    )
}

private data class FlagArgument(val value: String?, val remaining: List<String>) {
    companion object {
        fun parse(args: List<String>, flag: String): FlagArgument? {
            val index = args.indexOf(flag)
            val count = args.count { it == flag }
            val value = if (index >= 0) args.getOrNull(index + 1) else null
            return when {
                count == 0 -> FlagArgument(null, args)

                count != 1 || value.isNullOrBlank() || value.startsWith("--") -> null

                else -> FlagArgument(
                    value,
                    args.filterIndexed { position, _ -> position != index && position != index + 1 },
                )
            }
        }
    }
}
