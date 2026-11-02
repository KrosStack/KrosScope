package io.github.krosstack.kscope.benchmark.realrepos.measurement

import io.github.krosstack.kscope.benchmark.context.ContextBenchmark
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkBudgets
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkOptions
import io.github.krosstack.kscope.benchmark.context.ContextBenchmarkTask
import io.github.krosstack.kscope.benchmark.context.ContextProgressBoundary
import io.github.krosstack.kscope.benchmark.context.ContextProgressEvent
import io.github.krosstack.kscope.benchmark.context.ContextSetResult
import io.github.krosstack.kscope.benchmark.context.FileContextTaskCheckpoints
import io.github.krosstack.kscope.benchmark.localization.BenchmarkSet
import io.github.krosstack.kscope.benchmark.localization.LocalizationBenchmark
import io.github.krosstack.kscope.benchmark.localization.LocalizationCorpusSummary
import io.github.krosstack.kscope.benchmark.localization.LocalizationSetResult
import io.github.krosstack.kscope.benchmark.localization.LocalizationTask
import io.github.krosstack.kscope.benchmark.realrepos.RealCommitTask
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementShape
import io.github.krosstack.kscope.benchmark.realrepos.RealMeasurementStatus
import io.github.krosstack.kscope.benchmark.realrepos.RealRepositoryMeasurement
import io.github.krosstack.kscope.benchmark.realrepos.workspaceKey
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.ScanResult

internal class RealTaskRun(
    private val snapshot: RealWorkspaceSnapshot,
    private val task: RealCommitTask,
    private val identity: String,
    private val storage: RealRunStorage,
    private val budgets: List<TokenCount> = ContextBenchmarkBudgets.values,
) {
    private var phase = RealTaskPhase.SNAPSHOT
    private val clock = RealTaskClock()
    private var contextResumed = false
    private var scan: ScanResult? = null
    private var update: ScanResult? = null
    private var verification: ScanResult? = null
    private var localization: LocalizationSetResult? = null
    private var context: ContextSetResult? = null
    private var corpus: LocalizationCorpusSummary? = null

    fun execute(): RealRepositoryMeasurement = runCatching { measure() }.fold(
        onSuccess = { result(RealMeasurementStatus.COMPLETE) },
        onFailure = { failure ->
            if (failure is Error || failure is InterruptedException) throw failure
            result(RealMeasurementStatus.FAILED, "${phase.label}: ${failure.javaClass.simpleName}: ${failure.message}")
        },
    )

    private fun measure() {
        start(RealTaskPhase.SNAPSHOT)
        snapshot.prepare()
        start(RealTaskPhase.PARENT_CHECKOUT)
        snapshot.checkoutParents()
        KrosScope.open(snapshot.workspace, measuredOpenOptions(storage.index(task.workspaceKey()))).use { scope ->
            scanParent(scope)
            score(scope)
            updateChild(scope)
        }
    }

    private fun scanParent(scope: KrosScope) {
        timed(RealTaskPhase.PARENT_SCAN) { scan = scope.scan(rebuild = true) }
        check(scan?.diagnostics?.errors == 0) { "Parent scan reported errors." }
    }

    private fun score(scope: KrosScope) {
        val request = LocalizationTask(task.id, task.statement, task.goldFiles)
        timed(RealTaskPhase.LOCALIZATION) {
            val suite = LocalizationBenchmark.run(scope, snapshot.workspace, listOf(request))
            localization = suite.sets.single { it.kind == BenchmarkSet.ORIGINAL }
            corpus = suite.corpus
        }
        check(localization?.tasks?.singleOrNull()?.validGold == true) { "Localization task could not be measured." }
        val options = ContextBenchmarkOptions(
            budgets = budgets,
            methods = ContextBenchmarkOptions.DEFAULT_METHODS,
            progress = ::contextProgress,
            checkpoints = FileContextTaskCheckpoints(storage.checkpoints(task.id), identity),
        )
        timed(RealTaskPhase.CONTEXT) {
            context = ContextBenchmark.evaluate(
                scope,
                snapshot.workspace,
                listOf(ContextBenchmarkTask.fromLocalization(request)),
                options,
            )
        }
        check(context != null && !RealMeasurementShape.sourceIssues(context)) {
            "Context source spans could not be measured."
        }
        progress(if (contextResumed) "checkpoint-resumed-timing" else "actual-compilation-timing")
    }

    private fun updateChild(scope: KrosScope) {
        start(RealTaskPhase.CHILD_CHECKOUT)
        snapshot.checkoutCommits()
        timed(RealTaskPhase.INCREMENTAL_UPDATE) { update = scope.scan() }
        check(update?.diagnostics?.errors == 0) { "Incremental update reported errors." }
        if (task.verify) {
            timed(RealTaskPhase.VERIFICATION) { verification = scope.scan(verify = true) }
            check(verification?.diagnostics?.errors == 0 && verification?.verification?.matches == true) {
                "Incremental verification did not match the rebuild."
            }
        }
    }

    private fun start(next: RealTaskPhase) {
        phase = next
        progress("started")
    }

    private fun timed(next: RealTaskPhase, operation: () -> Unit) {
        start(next)
        clock.record(next, operation)
        progress("finished")
    }

    private fun contextProgress(event: ContextProgressEvent) {
        if (event.boundary == ContextProgressBoundary.CHECKPOINT_LOADED) contextResumed = true
        val method = event.method?.name.orEmpty()
        val budget = event.budget?.value?.toString().orEmpty()
        progress("${event.boundary.name} $method $budget".trim())
    }

    private fun progress(boundary: String) {
        System.err.println("real-repos ${task.repository} ${task.id} ${phase.label} $boundary")
    }

    private fun result(status: RealMeasurementStatus, failure: String? = null): RealRepositoryMeasurement =
        RealRepositoryMeasurement(
            task, identity, status, clock.timings.copy(contextResumed = contextResumed),
            corpus, localization, context, scan, update, verification, failure,
        )
}
