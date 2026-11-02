package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisModulePlanner
import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisPlan
import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisSharding
import io.github.krosstack.kscope.analyzer.kotlin.process.KotlinWorkerProcess
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerConcurrency
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLocator
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerOutcome
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerStart
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.ApiDigester
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.worker.WorkerModule
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Produces the facts of Kotlin and Java source sets by running the analyzer worker in a child JVM with one analysis
 * request. A request with enough source to share is split over up to [WorkerConcurrency.workers] workers that run at
 * the same time; each source set is still analysed by exactly one worker, against the same modules. Batches reach the
 * sink as the workers send them, one at a time. When a worker cannot be started or fails, every unit it did not
 * answer gets a batch with one error diagnostic, so that the scan can go on. [workerOutput] receives the lines the
 * workers write to their diagnostic stream. A reference that the compiler resolves into a module that the module of the
 * reference reaches only through heuristic dependencies is heuristic as well.
 */
public class KotlinAnalyzerProducer private constructor(
    private val launcher: WorkerLauncher,
    private val concurrency: () -> WorkerConcurrency,
    private val workerOutput: (String) -> Unit,
) : FactProducer {
    internal constructor(
        launcher: WorkerLauncher,
        concurrency: WorkerConcurrency = WorkerConcurrency.fromEnvironment(),
        workerOutput: (String) -> Unit,
    ) : this(launcher, { concurrency }, workerOutput)

    public constructor(workerOutput: (String) -> Unit = {}) : this({ WorkerSettings.NONE }, workerOutput)

    /** [settings] is asked each time workers are started, so that a change of the settings applies to the next scan. */
    public constructor(settings: () -> WorkerSettings, workerOutput: (String) -> Unit) : this(
        WorkerLauncher.fromEnvironment(settings),
        { WorkerConcurrency.fromEnvironment(settings()) },
        workerOutput,
    )

    override val descriptor: ProducerDescriptor = DESCRIPTOR
    override val apiDigester: ApiDigester = KotlinApiDigester(launcher, workerOutput)

    override fun produce(request: ProduceRequest, sink: FactSink) {
        val units = request.units.filter { it.sourceSet != null }
        if (units.isEmpty()) return
        val sources = ConsumedSources(request, DESCRIPTOR.consumes.map { it.id }.toSet())
        val plan = AnalysisModulePlanner().plan(sources.planInput())
        val sharding = AnalysisSharding()
        val shards = sharding.split(plan, sources.weigher(), concurrency().workers)
        val serialized = SerializedSink(sink)
        if (shards.size == 1) {
            analyseShard(shards.single(), plan, units, serialized)
        } else {
            analyseConcurrently(shards, plan, sharding.unitsPerShard(shards, units), serialized)
        }
    }

    private fun analyseConcurrently(
        shards: List<List<WorkerModule>>,
        plan: AnalysisPlan,
        units: List<List<AnalysisUnit>>,
        sink: FactSink,
    ) {
        val pool = Executors.newFixedThreadPool(shards.size) { task ->
            Thread(task, "kscope-kotlin-analysis").apply { isDaemon = true }
        }
        try {
            val pending = shards.indices.map { index ->
                pool.submit { analyseShard(shards[index], plan, units[index], sink) }
            }
            pending.forEach { future ->
                try {
                    future.get()
                } catch (failure: ExecutionException) {
                    throw failure.cause ?: failure
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun analyseShard(
        modules: List<WorkerModule>,
        plan: AnalysisPlan,
        units: List<AnalysisUnit>,
        sink: FactSink,
    ) {
        when (val start = launcher.start(workerOutput)) {
            is WorkerStart.Unavailable -> units.forEach {
                sink.accept(failureOf(it, plan, WorkerDiagnostics.UNAVAILABLE, unavailableMessage(start.reason)))
            }

            is WorkerStart.Started -> start.process.use { analyse(it, modules, plan, units, sink) }
        }
    }

    private fun analyse(
        process: KotlinWorkerProcess,
        modules: List<WorkerModule>,
        plan: AnalysisPlan,
        units: List<AnalysisUnit>,
        sink: FactSink,
    ) {
        val answered = mutableSetOf<OwnershipUnit>()
        val outcome = process.analyze(modules) { batch ->
            answered += batch.owner
            sink.accept(HeuristicReferences.applyTo(WorkerDiagnostics.adopt(batch, descriptor.producer, plan), plan))
        }
        if (outcome is WorkerOutcome.Failed) {
            units.filter { it.owner !in answered }.forEach {
                sink.accept(failureOf(it, plan, WorkerDiagnostics.FAILED, failedMessage(outcome.message)))
            }
        }
    }

    private fun unavailableMessage(reason: String) = "The Kotlin worker is unavailable: $reason"

    private fun failedMessage(reason: String): String {
        val starved = MEMORY_SIGNS.any { it in reason }
        val hint = " Raise the heap of the worker with ${WorkerLocator.HEAP_VARIABLE} or lower " +
            "${WorkerConcurrency.VARIABLE}."
        return "The Kotlin worker failed: $reason${if (starved) hint else ""}"
    }

    private fun failureOf(unit: AnalysisUnit, plan: AnalysisPlan, code: String, message: String): FactBatch {
        val fingerprint = unit.sourceSet?.let { plan.fingerprints[it.id] } ?: Fingerprint("")
        val diagnostic = WorkerDiagnostics.error(code, message)
        return FactBatch(descriptor.producer, unit.owner, fingerprint, listOf<Fact>(diagnostic))
    }

    private class SerializedSink(private val delegate: FactSink) : FactSink {
        override fun accept(batch: FactBatch) = synchronized(this) { delegate.accept(batch) }
    }

    private companion object {
        const val PRODUCER_ID = "kscope.analyzer.kotlin"
        const val PRODUCER_VERSION = "0.1.0"
        val MEMORY_SIGNS = listOf("out-of-memory", "did not answer")

        val DESCRIPTOR = ProducerDescriptor(
            producer = ProducerRef(PRODUCER_ID, PRODUCER_VERSION),
            consumes = setOf(
                KotlinApiDigester.KOTLIN_SOURCE,
                ArtifactType("java.source", ArtifactCategory.SOURCE),
            ),
            capabilities = setOf(
                AnalyzerCapability.DECLARATIONS,
                AnalyzerCapability.RESOLVED_REFERENCES,
                AnalyzerCapability.CALLS,
                AnalyzerCapability.INHERITANCE,
            ),
            granularity = OwnershipKind.SOURCE_SET,
        )
    }
}
