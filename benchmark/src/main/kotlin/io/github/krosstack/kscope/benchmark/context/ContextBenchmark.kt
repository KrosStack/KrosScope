package io.github.krosstack.kscope.benchmark.context

import io.github.krosstack.kscope.benchmark.graph.WorkspaceNames
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpus
import io.github.krosstack.kscope.benchmark.localization.LocalizationStatement
import io.github.krosstack.kscope.benchmark.localization.LocalizationStatements
import io.github.krosstack.kscope.benchmark.localization.LocalizationTask
import io.github.krosstack.kscope.benchmark.localization.PlainTextSearch
import io.github.krosstack.kscope.context.ContextCompiler
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.readWorkspaceFiles
import kotlinx.serialization.json.Json

public object ContextBenchmark {
    public fun run(
        scope: KrosScope,
        workspace: java.nio.file.Path,
        tasks: List<ContextBenchmarkTask>,
        heldOut: List<LocalizationStatement>? = null,
        options: ContextBenchmarkOptions = ContextBenchmarkOptions(),
    ): ContextBenchmarkResult {
        val original = tasks.sortedBy { it.id }
        val originals = tasks.map { LocalizationTask(it.id, it.statement, it.gold.files) }
        val errors = heldOut?.let { LocalizationStatements.validationErrors(originals, it) }.orEmpty()
        val inputs = linkedMapOf(BenchmarkSet.ORIGINAL to original)
        if (heldOut != null && errors.isEmpty()) {
            val statements = heldOut.associateBy { it.id }
            inputs[BenchmarkSet.HELD_OUT] = original.map { it.copy(statement = statements.getValue(it.id).statement) }
        }
        val runtime = ContextBenchmarkRuntime(scope, workspace, options)
        val sets = runtime.measure(inputs)
        val complete = sets.map { it.kind } == BenchmarkSet.entries
        val valid = errors.isEmpty() && sets.all { it.budgets.all { budget -> budget.valid } }
        return ContextBenchmarkResult(
            sets,
            options.estimator.id,
            runtime.corpus.summary,
            ContextMeasurementDefinition(),
            valid && complete && options.budgets == ContextBenchmarkBudgets.values &&
                sets.all { it.budgets.all { budget -> budget.tier1Passed } },
            complete,
            valid,
            errors,
        )
    }

    public fun evaluate(
        scope: KrosScope,
        workspace: java.nio.file.Path,
        tasks: List<ContextBenchmarkTask>,
        options: ContextBenchmarkOptions = ContextBenchmarkOptions(),
    ): ContextSetResult = ContextBenchmarkRuntime(scope, workspace, options)
        .measure(linkedMapOf(BenchmarkSet.ORIGINAL to tasks.sortedBy { it.id })).single()
}

private class ContextBenchmarkRuntime(
    private val scope: KrosScope,
    workspace: java.nio.file.Path,
    private val options: ContextBenchmarkOptions,
) {
    private val graph = scope.graph()
    private val names = WorkspaceNames.of(graph)
    val corpus = LocalizationCorpus.load(workspace, graph.artifacts(), names)
    private val documents: Map<ArtifactRef, SourceDocument> = graph.artifacts().mapNotNull { artifact ->
        corpus.documents[names.of(artifact.ref)]?.let { artifact.ref to SourceDocument(artifact.ref, it) }
    }.toMap()
    private val compiler = ContextCompiler(graph, scope.readWorkspaceFiles(), options.estimator)
    private val snapshot = ContextSnapshot.fingerprint(graph, corpus)

    fun measure(inputs: Map<BenchmarkSet, List<ContextBenchmarkTask>>): List<ContextSetResult> =
        PlainTextSearch(corpus.documents).use { raw ->
            PlainTextSearch(corpus.documents, removeEnglishStopWords = true).use { english ->
                val indexes = ContextBaselineIndexes(raw, english, corpus.documents.keys.sorted())
                inputs.map { (kind, tasks) -> set(kind, tasks, indexes) }
            }
        }

    private fun set(
        kind: BenchmarkSet,
        tasks: List<ContextBenchmarkTask>,
        indexes: ContextBaselineIndexes,
    ): ContextSetResult {
        val resolver = ContextGoldResolver(graph, documents, names)
        val evaluator = ContextTaskEvaluator(scope, documents, compiler, options, indexes)
        options.progress(ContextProgressEvent(ContextProgressBoundary.SET_STARTED, kind))
        val evaluated = tasks.map { task -> evaluate(kind, task, resolver, evaluator) }
        return ContextSetResult(
            kind,
            options.budgets.sortedBy { it.value }.map { budget ->
                ContextAggregates.budget(budget, evaluated.map { it.getValue(budget) }, options.methods)
            },
        ).also { options.progress(ContextProgressEvent(ContextProgressBoundary.SET_FINISHED, kind)) }
    }

    private fun evaluate(
        set: BenchmarkSet,
        task: ContextBenchmarkTask,
        resolver: ContextGoldResolver,
        evaluator: ContextTaskEvaluator,
    ): Map<TokenCount, TaskContextResult> {
        val trace = ContextProgressTrace(set, task.id, options.progress)
        val configuration = "${options.budgets}:${options.methods}:${options.estimator.id}"
        val request = ContextCheckpointRequest(
            set,
            task.id,
            contextFingerprint(snapshot + configuration + Json.encodeToString(task)),
        )
        trace.emit(ContextProgressBoundary.TASK_STARTED)
        val loaded = options.checkpoints?.load(request)?.takeIf { it.keys == options.budgets.toSet() }
        val result = if (loaded == null) {
            evaluator.evaluate(task, resolver.resolve(task.gold), set).also { results ->
                options.checkpoints?.save(request, results)
                if (options.checkpoints != null) trace.emit(ContextProgressBoundary.CHECKPOINT_SAVED)
            }
        } else {
            trace.emit(ContextProgressBoundary.CHECKPOINT_LOADED)
            loaded
        }
        trace.emit(ContextProgressBoundary.TASK_FINISHED)
        return result
    }
}
