package io.github.krosstack.kscope.benchmark

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkResult
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.ContextProgressEvent
import io.github.krosstack.kscope.benchmark.context.ContextReport
import io.github.krosstack.kscope.benchmark.context.FileContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.context.identity.ContextBenchmarkSelection
import io.github.krosstack.kscope.benchmark.context.semantic.ContextPipelineIdentityFactory
import io.github.krosstack.kscope.benchmark.context.semantic.RecipeGuardedCheckpoints
import io.github.krosstack.kscope.benchmark.context.semantic.SemanticContextGate
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.semantic.SemanticIndexingMode
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files

private const val USAGE = "usage: context-benchmark --mode default --out <directory> " +
    "[--workspace <directory>] [--gold <directory>] [--held-out <file-or-directory> | --original-only] " +
    "[--checkpoint-key <freeze-sha>] [--budgets 2000,4000,8000,16000] [--semantic on|off]"
private const val USAGE_ERROR = 2

internal class ContextBenchmarkArguments(
    val options: LocalizationOptions,
    val checkpointKey: String?,
    val budgets: List<TokenCount>,
    val semantic: MlMode = MlMode.OFF,
) {
    companion object {
        fun parse(args: List<String>): ContextBenchmarkArguments? {
            val checkpoint = FlagArgument.parse(args, "--checkpoint-key")
            val budgetFlag = checkpoint?.let { FlagArgument.parse(it.remaining, "--budgets") }
            val budgets = budgetFlag?.value?.let(ContextBenchmarkBudgets::parse)
            val semanticFlag = budgetFlag?.let { FlagArgument.parse(it.remaining, "--semantic") }
            val semantic = semanticFlag?.let { parseSemantic(it.value) } ?: return null
            val options = semanticFlag.takeIf { budgetFlag.value == null || budgets != null }
                ?.let { LocalizationOptions.parse(listOf("localization") + it.remaining.drop(1)) }
            return if (options == null || args.firstOrNull() != "context-benchmark") {
                null
            } else {
                ContextBenchmarkArguments(
                    options,
                    checkpoint.value,
                    budgets ?: ContextBenchmarkBudgets.values,
                    semantic,
                )
            }
        }

        private fun parseSemantic(value: String?): MlMode? = when (value) {
            null, "off" -> MlMode.OFF
            "on" -> MlMode.ON
            else -> null
        }
    }
}

internal fun runContextBenchmarkCommand(args: List<String>, print: (String) -> Unit): Int {
    val arguments = ContextBenchmarkArguments.parse(args)
    if (arguments == null) {
        print(USAGE)
        return USAGE_ERROR
    }
    return try {
        val result = measureContextBenchmark(arguments)
        val paths = arguments.options.conformance
        val expected = if (arguments.semantic == MlMode.ON) {
            LocalizationBenchmark.loadTasks(paths.gold.resolve("tasks")).map { it.id }.toSet()
        } else {
            null
        }
        val markdown = ContextReport.markdown(result, expected)
        Files.writeString(paths.out.resolve("context-benchmark.json"), BenchmarkJson.encode(result) + "\n")
        Files.writeString(paths.out.resolve("context-benchmark.md"), markdown)
        print(markdown)
        contextBenchmarkExitCode(result, arguments.semantic, expected)
    } catch (failure: IOException) {
        print("Context benchmark failed: ${failure.message}")
        1
    } catch (failure: UncheckedIOException) {
        print("Context benchmark failed: ${failure.message}")
        1
    } catch (failure: IllegalStateException) {
        print("Context benchmark failed: ${failure.message}")
        1
    } catch (failure: IllegalArgumentException) {
        print("Context benchmark failed: ${failure.message}")
        1
    }
}

private fun measureContextBenchmark(arguments: ContextBenchmarkArguments): ContextBenchmarkResult {
    val paths = arguments.options.conformance
    val semantic = arguments.semantic == MlMode.ON
    val recipe = semanticRecipe(arguments)
    val tasks = LocalizationBenchmark.loadTasks(paths.gold.resolve("tasks")).map(ContextBenchmarkTask::fromLocalization)
    val heldOut = arguments.options.heldOut?.let(LocalizationBenchmark::loadStatements)
    Files.createDirectories(paths.out)
    val index = paths.out.resolve("context-index")
    val openOptions = measuredOpenOptions(index).copy(
        semanticIndexing = if (semantic) SemanticIndexingMode.FOREGROUND else SemanticIndexingMode.OFF,
    )
    return KrosScope.open(paths.workspace, openOptions).use { scope ->
        val scan = scope.scan()
        check(scan.diagnostics.errors == 0) { "Scan failed: ${scan.diagnostics.errors} errors" }
        val pipeline = if (semantic) ContextPipelineIdentityFactory.read(scope, index) else null
        check(
            !semantic || ContextPipelineIdentityFactory.recipe() == recipe,
        ) { "Semantic ON recipe changed during scan." }
        val selection = ContextBenchmarkSelection(
            if (semantic) ContextBenchmarkOptions.SEMANTIC_METHODS else ContextBenchmarkOptions.DEFAULT_METHODS,
            pipeline,
        )
        val files = FileContextTaskCheckpoints(
            paths.out.resolve("context-checkpoints"),
            arguments.checkpointKey ?: "unfrozen",
            resume = arguments.checkpointKey != null,
            analysisKeys = selection.analysisKeys,
        )
        val guard = recipe?.let { RecipeGuardedCheckpoints(files, it) }
        val measurement = ContextBenchmarkOptions(
            budgets = arguments.budgets,
            selection = selection,
            progress = ::printProgress,
            checkpoints = guard ?: files,
        )
        ContextBenchmark.run(scope, paths.workspace, tasks, heldOut, measurement).also { guard?.checkRecipe() }
    }
}

private fun semanticRecipe(arguments: ContextBenchmarkArguments): ContentHash? {
    if (arguments.semantic == MlMode.OFF) return null
    val components = WorkspaceSettings.read(arguments.options.conformance.workspace).components
    check(listOf("semantic", "semantic-model").none { components[it] == false }) {
        "Semantic ON requires enabled semantic and semantic-model components."
    }
    return ContextPipelineIdentityFactory.recipe()
}

private fun printProgress(event: ContextProgressEvent) {
    System.err.println(
        "context-progress boundary=${event.boundary} set=${event.set} " +
            "task=${event.taskId ?: "-"} budget=${event.budget?.value ?: "-"} method=${event.method ?: "-"}",
    )
}

internal fun contextBenchmarkExitCode(
    result: ContextBenchmarkResult,
    mode: MlMode,
    expected: Set<String>? = null,
): Int {
    val passed = if (mode == MlMode.ON) {
        if (expected == null) {
            SemanticContextGate.evaluate(result).passed
        } else {
            SemanticContextGate.evaluate(result, expected).passed
        }
    } else {
        result.tier1Passed
    }
    return if (passed) 0 else 1
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
