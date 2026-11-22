package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextTaskEvaluator
import io.github.krosstack.kscope.benchmark.context.identity.ContextPipelineIdentity
import io.github.krosstack.kscope.benchmark.context.semantic.ContextPipelineIdentityFactory
import io.github.krosstack.kscope.benchmark.run.measuredOpenOptions
import io.github.krosstack.kscope.context.TokenEstimators
import io.github.krosstack.kscope.model.context.TokenCount
import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.retrieval.SemanticState
import io.github.krosstack.kscope.retrieval.SemanticStatus
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.context.ContextRequest
import io.github.krosstack.kscope.service.contextResult
import io.github.krosstack.kscope.service.semantic.advanceSemanticIndex
import io.github.krosstack.kscope.service.semantic.semanticNotice
import io.github.krosstack.kscope.service.semantic.semanticStatus
import io.github.krosstack.kscope.spi.context.TokenEstimator
import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.file.Path
import kotlin.math.ceil

internal class SemanticCostProbe(
    private val options: SemanticCostOptions,
    private val dependencies: Dependencies = Dependencies(),
    private val sampling: Sampling = Sampling(),
) {
    data class Dependencies(
        val open: (Path, OpenOptions) -> KrosScope = KrosScope::open,
        val now: () -> Long = System::nanoTime,
        val readIdentity: (KrosScope, Path) -> ContextPipelineIdentity = ContextPipelineIdentityFactory::read,
        val memory: ProcessResidentMemory = ProcessResidentMemory(),
        val advance: (KrosScope) -> Boolean = KrosScope::advanceSemanticIndex,
    )

    data class Sampling(
        val warmups: Int = 20,
        val repetitions: Int = 200,
        val estimator: TokenEstimator = TokenEstimators.create(),
    )

    @Serializable
    enum class Boundary { SERVICE_COLD, IDLE, ACTIVE_INDEXING }

    @Serializable
    enum class Operation { CONTEXT, ANALYZE, ANALYZE_ESTIMATES }

    @Serializable
    enum class Rejection { IDENTITY, SEMANTIC, INDEXING }

    @Serializable
    data class Sample(
        val boundary: Boundary,
        val operation: Operation,
        val ml: MlMode,
        val nanos: Long,
        val semantic: SemanticStatus,
        val pipeline: ContextPipelineIdentity?,
        val accepted: Boolean,
        val clientNanos: Long = nanos,
        val indexingActive: Boolean = true,
        val rejection: Rejection? = null,
        val queryIndex: Int = 0,
        val startedAtNanos: Long = 0,
    )

    @Serializable
    data class Summary(
        val operation: Operation,
        val ml: MlMode,
        val count: Int,
        val p50Nanos: Long?,
        val p95Nanos: Long?,
        val boundary: Boundary = Boundary.IDLE,
        val rejected: Int = 0,
    )

    @Serializable
    data class Result(
        val samples: List<Sample>,
        val summaries: List<Summary>,
        val memory: List<ProcessResidentMemory.Reading>,
        val notRun: List<String>,
        val machine: Map<String, String>? = null,
        val workload: SemanticCostOptions.Workload? = null,
        val warmups: Int = 20,
        val repetitions: Int = 200,
        val hostPid: Long? = null,
    )

    fun run(): Result {
        val missing = listOfNotNull(
            "Reference machine not run: descriptor missing.".takeIf { options.machine == null },
            "Reference workload not run: paraphrases missing.".takeIf { options.workload == null },
        )
        if (missing.isNotEmpty()) return Result(emptyList(), emptyList(), emptyList(), missing)
        val samples = mutableListOf<Sample>()
        val notRun = mutableListOf(
            "Formal one-shot process runs through exit minus info: not run.",
            "Full and incremental embedding, replicated 100000/300000 eligible vectors and retained disk: not run.",
        )
        val resident = dependencies.memory.start(dependencies.now)
        resident.use {
            cold(samples)
            dependencies.open(options.corpus, measuredOpenOptions(options.index)).use { scope ->
                check(scope.isIndexed()) { "The cost corpus needs a prepared index." }
                scope.graph()
                warm(scope, samples, Boundary.IDLE)
            }
            if (options.workload?.activeIndexing == true) {
                active(samples, notRun)
            } else {
                notRun += "Active-indexing workload: not run."
            }
        }
        return Result(
            samples,
            summaries(samples),
            resident.snapshot(),
            notRun + listOfNotNull(resident.limitation()),
            options.machine,
            options.workload,
            sampling.warmups,
            sampling.repetitions,
            ProcessHandle.current().pid(),
        )
    }

    private fun cold(samples: MutableList<Sample>) {
        for (operation in Operation.entries) {
            for (ml in MODES) {
                dependencies.open(options.corpus, measuredOpenOptions(options.index)).use { scope ->
                    check(scope.isIndexed()) { "The cost corpus needs a prepared index." }
                    val sample = call(scope, operation, ml, Boundary.SERVICE_COLD, 0)
                    val pipeline = audit(scope)
                    samples += admit(sample, pipeline)
                }
            }
        }
    }

    private fun warm(scope: KrosScope, samples: MutableList<Sample>, boundary: Boundary) {
        for (operation in Operation.entries) {
            repeat(sampling.warmups) { index -> MODES.forEach { call(scope, operation, it, boundary, index) } }
            val before = audit(scope)
            val measured = (0 until sampling.repetitions).flatMap { index ->
                MODES.map { admit(call(scope, operation, it, boundary, index), before) }
            }
            val after = audit(scope)
            samples += measured.map {
                if (before == after) {
                    it
                } else {
                    it.copy(accepted = false, pipeline = null, rejection = Rejection.IDENTITY)
                }
            }
        }
    }

    private fun active(samples: MutableList<Sample>, notRun: MutableList<String>) {
        val open = measuredOpenOptions(options.index).copy(
            semanticIndexing = io.github.krosstack.kscope.service.semantic.SemanticIndexingMode.DEFERRED,
        )
        dependencies.open(options.corpus, open).use { scope ->
            if (!dependencies.advance(scope)) {
                val status = scope.semanticStatus()
                val reason = scope.semanticNotice() ?: if (status.state == SemanticState.USED) {
                    "no pending index job"
                } else {
                    "semantic index unavailable (${status.state})"
                }
                notRun += "Active-indexing workload: $reason, not run."
            } else {
                warm(scope, samples, Boundary.ACTIVE_INDEXING)
            }
        }
    }

    private fun call(scope: KrosScope, operation: Operation, ml: MlMode, boundary: Boundary, index: Int): Sample {
        val queries = checkNotNull(options.workload).queries
        val query = queries[index % queries.size]
        val clientStart = dependencies.now()
        val indexing = boundary != Boundary.ACTIVE_INDEXING || dependencies.advance(scope)
        val start = dependencies.now()
        val semantic = when (operation) {
            Operation.CONTEXT -> scope.contextResult(
                ContextRequest(query, TokenCount(CONTEXT_BUDGET), estimator = sampling.estimator, ml = ml),
            ).semantic

            Operation.ANALYZE -> scope.analyze(query, includeContextEstimates = false, ml = ml).semantic

            Operation.ANALYZE_ESTIMATES -> scope.analyze(query, ml = ml).semantic
        }
        val end = dependencies.now()
        val nanos = end - start
        val clientNanos = end - clientStart
        check(nanos >= 0 && clientNanos >= 0) { "Monotonic clock produced a negative duration." }
        return Sample(
            boundary, operation, ml, nanos, semantic, null, false, clientNanos, indexing,
            queryIndex = index % queries.size,
            startedAtNanos = start,
        )
    }

    private fun audit(scope: KrosScope): ContextPipelineIdentity? = try {
        dependencies.readIdentity(scope, options.index)
    } catch (_: IllegalStateException) {
        null
    } catch (_: IOException) {
        null
    }

    private fun admit(sample: Sample, pipeline: ContextPipelineIdentity?): Sample {
        val semantic = if (sample.ml == MlMode.OFF) {
            sample.semantic == SemanticStatus.OFF
        } else {
            ContextTaskEvaluator.complete(sample.semantic, pipeline)
        }
        val rejection = when {
            pipeline != options.pipeline || pipeline.generation == null -> Rejection.IDENTITY
            !sample.indexingActive -> Rejection.INDEXING
            !semantic -> Rejection.SEMANTIC
            else -> null
        }
        return sample.copy(pipeline = pipeline, accepted = rejection == null, rejection = rejection)
    }

    private fun summaries(samples: List<Sample>): List<Summary> =
        samples.map { it.boundary }.distinct().filter { it != Boundary.SERVICE_COLD }.flatMap { boundary ->
            Operation.entries.flatMap { operation ->
                MODES.map { ml ->
                    val values = samples.filter {
                        it.boundary == boundary && it.operation == operation && it.ml == ml && it.accepted
                    }.map { it.nanos }.sorted()
                    val rejected = samples.count {
                        it.boundary == boundary && it.operation == operation && it.ml == ml && !it.accepted
                    }
                    Summary(
                        operation,
                        ml,
                        values.size,
                        percentile(values, MEDIAN),
                        percentile(values, P95),
                        boundary,
                        rejected,
                    )
                }
            }
        }

    private fun percentile(values: List<Long>, probability: Double): Long? =
        values.takeIf { it.isNotEmpty() }?.get(ceil(values.size * probability).toInt() - 1)

    private companion object {
        val MODES = listOf(MlMode.OFF, MlMode.ON)
        const val CONTEXT_BUDGET = 8_000
        const val MEDIAN = 0.5
        const val P95 = 0.95
    }
}
