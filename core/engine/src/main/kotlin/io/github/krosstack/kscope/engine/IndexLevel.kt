package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.inspection.IndexLevels
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.store.UnitKind

/** Whether a persisted repository model belongs to a registered provider that requires build execution. */
public fun ScanEngine.indexRequiresTrust(): Boolean {
    if (!hasIndexedFiles()) return false
    return IndexLevels.requiresTrust(recordedUnits(), trustedBuildProducers())
}

/** The level the persisted index was built at, or null when the workspace has no index with files in it. */
public fun ScanEngine.indexLevel(): ScanLevel? =
    if (hasIndexedFiles()) IndexLevels.of(recordedUnits(), trustedBuildProducers()) else null

internal fun ScanEngine.trustedBuildProducers(): Set<String> =
    registry.buildModelProviders.filter { it.requiresTrust }.map { it.producer.id }.toSet()

private fun ScanEngine.recordedUnits(): List<UnitKind> =
    (graph() as GraphStore).units().map { UnitKind(it.producer, it.owner.kind) }
