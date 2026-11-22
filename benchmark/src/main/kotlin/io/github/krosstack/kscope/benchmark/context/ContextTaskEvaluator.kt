package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.context.identity.ContextAnalysisKey
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.PlainTextSearch
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.ContextInput
import io.github.krosstack.kscope.model.context.ContextLevel
import io.github.krosstack.kscope.model.context.ContextPackage
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.semantic.store.GenerationCurrent
import io.github.krosstack.kscope.semantic.store.GenerationStore
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.contextInput
import java.nio.file.Path

internal class ContextTaskEvaluator(
    private val scope: KrosScope,
    private val documents: Map<ArtifactRef, SourceDocument>,
    private val compiler: ContextCompiler,
    private val options: ContextBenchmarkOptions,
    private val baselines: ContextBaselineIndexes,
) {
    private val names = WorkspaceNames.of(scope.graph())
    private val generations = options.selection.pipeline?.let { GenerationStore(Path.of(scope.info().indexLocation)) }

    private data class Input(val context: ContextInput, val semantic: SemanticStatus, val complete: Boolean)

    fun evaluate(
        task: ContextBenchmarkTask,
        gold: ResolvedContextGold,
        set: BenchmarkSet,
        acceptsAbsentGold: Boolean,
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
                val semantic = options.selection.analysisKey(method)?.let { inputs.getValue(it).semantic }
                ContextMethodResult(scores, context, costs[method] ?: compilerCosts(), semantic).also {
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
                valid(task, gold, methods, acceptsAbsentGold) && inputs.values.all { it.complete },
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

    private fun inputs(task: String, trace: ContextProgressTrace): Map<ContextAnalysisKey, Input> =
        options.selection.analysisKeys.associateWith { key ->
            val method = options.methods.first { options.selection.analysisKey(it) == key }
            trace.emit(ContextProgressBoundary.ANALYSIS_STARTED, method = method)
            val selected = key.ml == MlMode.OFF || matchesGeneration()
            val analysis = scope.analyze(task, key.stage, includeContextEstimates = false, ml = key.ml)
            val complete = key.ml == MlMode.OFF ||
                (selected && complete(analysis.semantic, options.selection.pipeline) && matchesGeneration())
            Input(scope.contextInput(analysis), analysis.semantic, complete).also {
                trace.emit(ContextProgressBoundary.ANALYSIS_FINISHED, method = method)
            }
        }

    private fun matchesGeneration(): Boolean {
        val pipeline = options.selection.pipeline ?: return false
        val selected = generations?.tryCurrent(pipeline.provider) as? GenerationCurrent.Current
        return pipeline.generation?.let { selected?.name == "embeddings-${pipeline.provider}-${it.value}.bin" } ?: false
    }

    private fun packageFor(
        task: String,
        method: ContextBenchmarkMethod,
        budget: TokenCount,
        inputs: Map<ContextAnalysisKey, Input>,
        paths: Map<ContextBenchmarkMethod, List<String>>,
    ): ContextPackage = options.selection.analysisKey(method)?.let {
        compiler.compile(inputs.getValue(it).context, ContextLevel.RECOMMENDED, budget)
    }
        ?: ContextFilePacker(documents, options.estimator, names).pack(task, paths.getValue(method), budget)

    private fun valid(
        task: ContextBenchmarkTask,
        gold: ResolvedContextGold,
        methods: Map<ContextBenchmarkMethod, ContextMethodResult>,
        acceptsAbsentGold: Boolean,
    ): Boolean = task.id.isNotBlank() && task.statement.isNotBlank() &&
        task.gold.files.size + task.gold.symbols.size > 0 &&
        (gold.files.size + gold.symbols.size > 0 || acceptsAbsentGold) &&
        methods.values.all { it.scores.sourceIssues.isEmpty() }

    private fun compilerCosts(): FullFileCoverageCosts = FullFileCoverageCosts(
        reason90 = COMPILER_COST_REASON,
        reason100 = COMPILER_COST_REASON,
    )

    companion object {
        fun complete(status: SemanticStatus?, pipeline: ContextPipelineIdentity?): Boolean =
            status?.state == SemanticState.USED && status.behindChunks == 0 && pipeline != null &&
                status.provider == pipeline.provider && status.model == pipeline.model

        private const val COMPILER_COST_REASON =
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
