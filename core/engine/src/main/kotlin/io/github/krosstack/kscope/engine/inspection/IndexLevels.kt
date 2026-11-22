package io.github.krosstack.kscope.engine.inspection

import io.github.krosstack.kscope.engine.ScanLevel
import io.github.krosstack.kscope.engine.pipeline.LevelUnit
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.store.UnitKind

internal object IndexLevels {
    fun of(units: Collection<UnitKind>, trustedProducers: Set<String>): ScanLevel = when {
        requiresTrust(units, trustedProducers) -> ScanLevel.EXACT
        LevelUnit.recordedIn(units) -> ScanLevel.SYNTAX
        else -> ScanLevel.RESOLVED
    }

    fun requiresTrust(units: Collection<UnitKind>, trustedProducers: Set<String>): Boolean =
        units.any { it.kind == OwnershipKind.REPOSITORY && it.producer.id in trustedProducers }
}
