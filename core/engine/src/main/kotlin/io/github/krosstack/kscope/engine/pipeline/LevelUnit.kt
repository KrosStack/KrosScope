package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.spi.graph.GraphStore

/**
 * The record that an index was built at level `syntax`: an empty unit owned by the workspace. The other levels leave
 * no unit, so an index built at `resolved` is as it always was; `exact` shows in the build model it stores.
 */
internal object LevelUnit {
    val producer = ProducerRef("kscope.level", "1")

    private const val KEY = "workspace"

    fun plan(level: ScanLevel): PlannedUnit? {
        if (level != ScanLevel.SYNTAX) return null
        val fingerprint = FingerprintBuilder().add(producer.id).add(producer.version).add(level.name).build()
        return PlannedUnit(producer, OwnershipUnit(OwnershipKind.WORKSPACE, KEY), fingerprint)
    }

    fun recordedIn(store: GraphStore): Boolean = store.units().any { it.producer == producer }

    fun batch(unit: PlannedUnit): FactBatch = FactBatch(unit.producer, unit.owner, unit.fingerprint, emptyList())
}
