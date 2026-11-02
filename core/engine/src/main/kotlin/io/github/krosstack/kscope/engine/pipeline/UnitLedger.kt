package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.spi.graph.GraphStore

/** Tracks which units an update wants, compares them with the stored fingerprints and counts the outcome. */
internal class UnitLedger(private val store: GraphStore) {
    private val stored: Map<UnitKey, Fingerprint> =
        store.units().associate { UnitKey(it.producer, it.owner) to it.fingerprint }
    private val wanted = linkedMapOf<UnitKey, PlannedUnit>()

    var produced = 0
        private set
    var skipped = 0
        private set
    var removed = 0
        private set

    fun isCurrent(unit: PlannedUnit): Boolean = stored[unit.key] == unit.fingerprint

    /** Registers the unit as wanted. Returns true when it must be produced; a current unit counts as skipped. */
    fun claimIfStale(unit: PlannedUnit): Boolean {
        wanted[unit.key] = unit
        val stale = !isCurrent(unit)
        if (!stale) skipped++
        return stale
    }

    fun commit(batch: FactBatch) {
        store.replace(batch)
        produced++
    }

    fun wantedUnits(): List<PlannedUnit> = wanted.values.toList()

    fun removeUnwanted() {
        val unwanted = store.units().filter { UnitKey(it.producer, it.owner) !in wanted }
        store.transaction { unwanted.forEach { store.remove(it.producer, it.owner) } }
        removed += unwanted.size
    }
}
