package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.PlainTextSearch
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.RetrievalStage
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.contextInput

internal class ContextTaskEvaluator(
    private val scope: KrosScope,
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val compiler: ContextCompiler,
    private val options: ContextBenchmarkOptions,
    private val baselines: ContextBaselineIndexes,
) {
    private val names = WorkspaceNames.of(scope.graph())

    fun evaluate(
        task: ContextBenchmarkTask,
        gold: ResolvedContextGold,
        set: BenchmarkSet,
    ): Map<TokenCount, TaskContextResult> {
        val trace = ContextProgressTrace(set, task.id, options.progress)
        val inputs = inputs(task.statement, trace)
        val paths = baselines.rankings(task.statement)
        val costs = costs(task.statement, gold.files, paths, trace)
        val measured = options.budgets.sortedBy { it.value }.associateWith { budget ->
            trace.emit(ContextProgressBoundary.BUDGET_STARTED, budget)
            val methods = options.methods.associateWith { method ->
                trace.emit(ContextProgressBoundary.METHOD_STARTED, budget, method)
                val context = packageFor(task.statement, method, budget, inputs, paths)
                val scores = ContextMetrics.score(context, gold, documents, options.estimator, names)
                ContextMethodResult(scores, context, costs[method] ?: compilerCosts()).also {
                    trace.emit(ContextProgressBoundary.METHOD_FINISHED, budget, method)
                }
            }
            trace.emit(ContextProgressBoundary.BUDGET_FINISHED, budget)
            TaskContextResult(
                task.id,
                task.statement,
                task.gold,
                methods,
                gold.excludedMissingFiles,
                gold.unmappedSymbols,
                gold.files.map(names::of).sorted(),
                valid(task, gold, methods),
            )
        }
        return CompilerCoverageCosts.annotate(measured, options.methods)
    }

    private fun costs(
        task: String,
        goldFiles: Set<ArtifactRef>,
        paths: Map<ContextBenchmarkMethod, List<String>>,
        trace: ContextProgressTrace,
    ): Map<ContextBenchmarkMethod, FullFileCoverageCosts> {
        val packer = ContextFilePacker(documents, options.estimator, names)
        return paths.mapValues { (method, ranked) ->
            trace.emit(ContextProgressBoundary.COVERAGE_COST_STARTED, method = method)
            packer.costs(task, ranked, goldFiles).also {
                trace.emit(ContextProgressBoundary.COVERAGE_COST_FINISHED, method = method)
            }
        }
    }

    private fun inputs(task: String, trace: ContextProgressTrace): Map<RetrievalStage, ContextInput> =
        options.methods.mapNotNull { it.stage }.distinct().associateWith { stage ->
            val method = options.methods.first { it.stage == stage }
            trace.emit(ContextProgressBoundary.ANALYSIS_STARTED, method = method)
            scope.analyze(task, stage, includeContextEstimates = false).contextInput().also {
                trace.emit(ContextProgressBoundary.ANALYSIS_FINISHED, method = method)
            }
        }

    private fun packageFor(
        task: String,
        method: ContextBenchmarkMethod,
        budget: TokenCount,
        inputs: Map<RetrievalStage, ContextInput>,
        paths: Map<ContextBenchmarkMethod, List<String>>,
    ): ContextPackage = method.stage?.let { compiler.compile(inputs.getValue(it), ContextLevel.RECOMMENDED, budget) }
        ?: ContextFilePacker(documents, options.estimator, names).pack(task, paths.getValue(method), budget)

    private fun valid(
        task: ContextBenchmarkTask,
        gold: ResolvedContextGold,
        methods: Map<ContextBenchmarkMethod, ContextMethodResult>,
    ): Boolean = task.id.isNotBlank() && task.statement.isNotBlank() && gold.files.size + gold.symbols.size > 0 &&
        methods.values.all { it.scores.sourceIssues.isEmpty() }

    private fun compilerCosts(): FullFileCoverageCosts = FullFileCoverageCosts(
        reason90 = COMPILER_COST_REASON,
        reason100 = COMPILER_COST_REASON,
    )

    private companion object {
        const val COMPILER_COST_REASON =
            "The greedy compiler's minimal required budget was not measured; selection can be nonmonotonic."
    }
}

internal class ContextBaselineIndexes(
    private val raw: PlainTextSearch,
    private val english: PlainTextSearch,
    private val paths: List<String>,
) {
    fun rankings(task: String): Map<ContextBenchmarkMethod, List<String>> = linkedMapOf(
        ContextBenchmarkMethod.BM25_RAW to raw.search(task),
        ContextBenchmarkMethod.BM25_ENGLISH to english.search(task),
        ContextBenchmarkMethod.PATH_ORDER to paths,
    )
}
