package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.engine.progress.ScanStages
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader

internal class PendingLink(val linker: BoundaryLinker, val unit: PlannedUnit)

/**
 * Linkers see the whole graph, so each one is re-run whenever any other unit changed, or any linker before it did.
 * Its previous output is removed before it runs, so that it never reads its own stale facts. Afterwards the engine
 * checks that it answered each symbolic reference of its namespaces exactly once.
 */
internal class LinkingStage(private val linkers: List<BoundaryLinker>, private val session: UpdateSession) {
    /** Call after every non-linker unit has been claimed. */
    fun plan(): List<PendingLink> {
        val base = FingerprintBuilder().addAll(session.ledger.wantedUnits().flatMap(::partsOf)).digestHex()
        var previous = base
        return linkers.mapNotNull { linker ->
            val producer = linker.descriptor.producer
            val fingerprint = FingerprintBuilder().add(producer.id).add(producer.version).add(previous).build()
            previous = fingerprint.value
            val unit = PlannedUnit(producer, OwnershipUnit(OwnershipKind.WORKSPACE, WORKSPACE_KEY), fingerprint)
            if (session.ledger.claimIfStale(unit)) PendingLink(linker, unit) else null
        }
    }

    fun execute(pending: List<PendingLink>) {
        val units = UnitProgress(session.progress, ScanStages.LINKING, pending.size, "linkers")
        units.advance()
        pending.forEach {
            link(it)
            units.advance(1)
        }
    }

    private fun link(pending: PendingLink) {
        val unit = pending.unit
        val facts = mutableListOf<Fact>()
        session.store.transaction {
            session.store.remove(unit.producer, unit.owner)
            pending.linker.link(ReadOnlyGraph(session.store), FactSink { facts += it.facts })
            val findings = SymbolicReferenceAudit(pending.linker).findings(session.store, facts)
            session.ledger.commit(FactBatch(unit.producer, unit.owner, unit.fingerprint, facts + findings))
        }
    }

    private fun partsOf(unit: PlannedUnit): List<String> =
        listOf(unit.producer.id, unit.producer.version, unit.owner.kind.name, unit.owner.key, unit.fingerprint.value)

    private class ReadOnlyGraph(graph: GraphReader) : GraphReader by graph

    private companion object {
        const val WORKSPACE_KEY = "workspace"
    }
}
