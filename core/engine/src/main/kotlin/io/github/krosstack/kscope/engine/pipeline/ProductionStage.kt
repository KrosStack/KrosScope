package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.incremental.PlannedWork
import io.github.krosstack.kscope.engine.incremental.ProducerApiDigests
import io.github.krosstack.kscope.engine.incremental.UnitPlanner
import io.github.krosstack.kscope.engine.progress.ScanStages
import io.github.krosstack.kscope.spi.FactProducer
import io.github.krosstack.kscope.spi.SyntheticSource

/** The stale units of one producer, found during planning and produced once the obsolete units are removed. */
internal class PendingProduction(val producer: FactProducer, val job: CapabilityJob)

/** Plans and runs the fact producers; only units whose fingerprint changed are handed to them. */
internal class ProductionStage(
    private val producers: List<FactProducer>,
    private val planner: UnitPlanner,
    private val session: UpdateSession,
    private val runner: CapabilityRunner,
    private val jobs: RepositoryJobs,
) {
    fun plan(apiDigests: Map<String, ProducerApiDigests>): List<PendingProduction> = producers.mapNotNull { producer ->
        val api = apiDigests[producer.descriptor.producer.id]
        val stale = planner.plan(producer.descriptor, api).filter { session.ledger.claimIfStale(it.unit) }
        stale.takeIf { it.isNotEmpty() }?.let { PendingProduction(producer, jobFor(producer, it)) }
    }

    private fun jobFor(producer: FactProducer, stale: List<PlannedWork>) =
        CapabilityJob(producer.descriptor.producer, stale, stale.map { it.unit })

    fun execute(pending: List<PendingProduction>, synthetic: List<SyntheticSource>) {
        val units = UnitProgress(session.progress, ScanStages.PRODUCTION, pending.sumOf { it.job.store.size }, "units")
        units.advance()
        pending.forEach { production ->
            jobs.split(production.job).forEach { job ->
                runner.run(job, synthetic, units) { request, sink -> production.producer.produce(request, sink) }
            }
        }
    }
}
