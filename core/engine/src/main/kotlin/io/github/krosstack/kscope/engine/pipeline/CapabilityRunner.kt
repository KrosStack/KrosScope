package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProduceRequest
import io.github.krosstack.kscope.spi.RepositoryCheckout
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.WorkspaceFiles

/** Collects what a capability emits, keeping only batches that name the expected producer and a requested owner. */
internal class BatchCollector(
    private val producer: ProducerRef,
    private val owners: Set<OwnershipUnit>,
    private val onFirstAnswer: (OwnershipUnit) -> Unit = {},
) : FactSink {
    private val factsByOwner = linkedMapOf<OwnershipUnit, MutableList<Fact>>()
    val rejections = mutableListOf<Diagnostic>()
    val answered: Set<OwnershipUnit> get() = factsByOwner.keys

    override fun accept(batch: FactBatch) {
        when {
            batch.producer != producer -> reject(batch, "it names producer ${batch.producer.id}")
            batch.owner !in owners -> reject(batch, "it names an owner that was not requested")
            else -> accept(batch.owner, batch.facts)
        }
    }

    private fun accept(owner: OwnershipUnit, facts: List<Fact>) {
        val first = owner !in factsByOwner
        factsByOwner.getOrPut(owner) { mutableListOf() } += facts
        if (first) onFirstAnswer(owner)
    }

    /**
     * A unit the capability did not answer is stored empty, so that it is not retried until its inputs change. A unit
     * whose only answers are errors failed, perhaps for lack of memory: its errors are kept, under a fingerprint that
     * no plan produces, so that the next update runs the unit again.
     */
    fun batchFor(unit: PlannedUnit): FactBatch {
        val facts = factsByOwner[unit.owner].orEmpty()
        return FactBatch(unit.producer, unit.owner, if (facts.areOnlyErrors()) RETRY else unit.fingerprint, facts)
    }

    private fun List<Fact>.areOnlyErrors() = isNotEmpty() && all {
        it is Fact.OfDiagnostic && it.value.severity == DiagnosticSeverity.ERROR
    }

    private fun reject(batch: FactBatch, reason: String) {
        rejections += Diagnostic(
            severity = DiagnosticSeverity.ERROR,
            code = ReasonCode("engine.batch-rejected"),
            message = "A batch from ${producer.id} for ${batch.owner.kind} ${batch.owner.key} was dropped: $reason.",
            location = null,
        )
    }

    private companion object {
        val RETRY = Fingerprint("failed")
    }
}

/** Runs one capability over its units and stores the results of the units that must be stored. */
internal class CapabilityRunner(
    private val session: UpdateSession,
    private val model: ResolvedBuildModel,
    private val files: WorkspaceFiles,
    private val artifacts: List<Artifact>,
    private val checkouts: List<RepositoryCheckout>,
) {
    fun <T> run(
        job: CapabilityJob,
        synthetic: List<SyntheticSource>,
        progress: UnitProgress? = null,
        action: (ProduceRequest, FactSink) -> T,
    ): T {
        val stored = job.store.mapTo(mutableSetOf()) { it.owner }
        val collector = BatchCollector(job.producer, job.requested.map { it.unit.owner }.toSet()) { owner ->
            if (owner in stored) progress?.advance(1)
        }
        val request = ProduceRequest(
            job.requested.map { it.analysis },
            job.modelIn(model.model),
            files,
            synthetic,
            model.classpaths,
            job.artifactsIn(artifacts),
            checkouts.checkoutOf(job),
        )
        val result = action(request, collector)
        progress?.advance(stored.count { it !in collector.answered })
        session.notices += collector.rejections
        session.store.transaction { job.store.forEach { session.ledger.commit(collector.batchFor(it)) } }
        return result
    }
}

private fun List<RepositoryCheckout>.checkoutOf(job: CapabilityJob): RepositoryCheckout? {
    val repository = job.confinedTo?.value ?: job.requested.map { it.unit.owner }
        .filter { it.kind == OwnershipKind.REPOSITORY }.map { it.key }.distinct().singleOrNull()
    return firstOrNull { it.repository.id.value == repository } ?: firstOrNull()
}
