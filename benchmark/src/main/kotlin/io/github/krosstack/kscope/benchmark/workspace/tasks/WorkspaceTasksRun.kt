package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceGoldLoader
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceOutputs
import io.github.krosstack.kscope.benchmark.workspace.WorkspacePreparer
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import java.nio.file.Files
import java.nio.file.Path

/**
 * Measures the tasks of [goldDirectory] on a copy of the workspace fixture: each repository becomes a Git repository,
 * the files that a build leaves are written, and one scan serves the localization and the context benchmark. The
 * fixture stays untouched.
 */
internal class WorkspaceTasksRun(
    private val fixture: Path,
    private val goldDirectory: Path,
    private val budgets: List<TokenCount>,
) {
    fun run(): WorkspaceTasksResult {
        val tasks = WorkspaceTaskLoader.load(goldDirectory)
        val outputs = WorkspaceOutputs.load(WorkspaceGoldLoader.outputsFile(goldDirectory))
        val scratch = Files.createTempDirectory("kscope-workspace-tasks-")
        try {
            val root = Files.createDirectories(scratch.resolve("workspace"))
            val repositories = WorkspacePreparer().prepare(fixture, root)
            WorkspaceOutputs.write(root, outputs)
            val scoring = KrosScope.open(root, measuredOpenOptions(scratch.resolve("index"))).use { scope ->
                check(scope.scan().diagnostics.errors == 0) { "Scan of the workspace failed." }
                measure(scope, root, tasks)
            }
            return WorkspaceTaskResults(tasks, scoring).build(repositories, budgets)
        } finally {
            scratch.toFile().deleteRecursively()
        }
    }

    private fun measure(scope: KrosScope, root: Path, tasks: List<WorkspaceTaskFile>): WorkspaceTaskScoring {
        val requests = tasks.map { it.localization }
        val suite = LocalizationBenchmark.run(scope, root, requests)
        val localization = suite.sets.single { it.kind == BenchmarkSet.ORIGINAL }
        val options = ContextBenchmarkOptions(budgets = budgets)
        val inputs = requests.map(ContextBenchmarkTask::fromLocalization)
        val context = ContextBenchmark.evaluate(scope, root, inputs, options)
        return WorkspaceTaskScoring(localization, context, options.methods)
    }
}
