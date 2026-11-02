package io.github.krosstack.kscope.analyzer.kotlin

import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisModulePlanner
import io.github.krosstack.kscope.analyzer.kotlin.planning.AnalysisPlan
import io.github.krosstack.kscope.analyzer.kotlin.process.KotlinWorkerProcess
import io.github.krosstack.kscope.analyzer.kotlin.process.WorkerLauncher
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
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.worker.WorkerModule

/**
 * Produces the declarations of Kotlin and Java source sets from their syntax alone: the worker reads the files
 * without an analysis session, so nothing is resolved and no reference, call or inheritance fact exists. Each source
 * set answers with its symbols and one note that references were not analysed. When the worker cannot be started or
 * fails, every unit it did not answer gets a batch with one error diagnostic, so that the scan can go on.
 */
public class KotlinSyntaxProducer private constructor(
    private val launcher: WorkerLauncher,
    private val workerOutput: (String) -> Unit,
) : FactProducer {
    public constructor(settings: () -> WorkerSettings = { WorkerSettings.NONE }, workerOutput: (String) -> Unit = {}) :
        this(WorkerLauncher.fromEnvironment(settings), workerOutput)

    internal constructor(launcher: WorkerLauncher) : this(launcher, {})

    override val descriptor: ProducerDescriptor = DESCRIPTOR

    override fun produce(request: ProduceRequest, sink: FactSink) {
        val units = request.units.filter { it.sourceSet != null }
        if (units.isEmpty()) return
        val sources = ConsumedSources(request, DESCRIPTOR.consumes.map { it.id }.toSet())
        val plan = AnalysisModulePlanner().plan(sources.planInput())
        when (val start = launcher.start(workerOutput)) {
            is WorkerStart.Unavailable -> units.forEach {
                val message = "The Kotlin worker is unavailable: ${start.reason}"
                sink.accept(failureOf(it, plan, WorkerDiagnostics.UNAVAILABLE, message))
            }

            is WorkerStart.Started -> start.process.use { declare(it, plan.modules, plan, units, sink) }
        }
    }

    private fun declare(
        process: KotlinWorkerProcess,
        modules: List<WorkerModule>,
        plan: AnalysisPlan,
        units: List<AnalysisUnit>,
        sink: FactSink,
    ) {
        val answered = mutableSetOf<OwnershipUnit>()
        val outcome = process.analyze(modules, declarationsOnly = true) { batch ->
            answered += batch.owner
            sink.accept(WorkerDiagnostics.adopt(batch, descriptor.producer, plan))
        }
        if (outcome is WorkerOutcome.Failed) {
            units.filter { it.owner !in answered }.forEach {
                val message = "The Kotlin worker failed: ${outcome.message}"
                sink.accept(failureOf(it, plan, WorkerDiagnostics.FAILED, message))
            }
        }
    }

    private fun failureOf(unit: AnalysisUnit, plan: AnalysisPlan, code: String, message: String): FactBatch {
        val fingerprint = unit.sourceSet?.let { plan.fingerprints[it.id] } ?: Fingerprint("")
        val facts = listOf<Fact>(WorkerDiagnostics.error(code, message))
        return FactBatch(descriptor.producer, unit.owner, fingerprint, facts)
    }

    private companion object {
        val DESCRIPTOR = ProducerDescriptor(
            producer = ProducerRef("kscope.analyzer.kotlin.syntax", "0.1.0"),
            consumes = setOf(
                KotlinApiDigester.KOTLIN_SOURCE,
                ArtifactType("java.source", ArtifactCategory.SOURCE),
            ),
            capabilities = setOf(AnalyzerCapability.DECLARATIONS),
            granularity = OwnershipKind.SOURCE_SET,
        )
    }
}
