package io.github.krosstack.kscope.engine.scope

import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.Repository

/**
 * The record that an index was built with analyzer groups switched off: an empty unit whose fingerprint is the key of
 * the sorted names. With every group on there is no unit, so an index built without settings is as it always was.
 */
internal object AnalyzerUnit {
    val producer = ProducerRef("kscope.analyzers", "1")

    fun plan(repository: Repository, disabled: Set<String>): FactBatch? {
        val key = keyOf(disabled) ?: return null
        return FactBatch(producer, OwnershipUnit(OwnershipKind.REPOSITORY, repository.id.value), key, emptyList())
    }

    fun keyOf(disabled: Set<String>): Fingerprint? {
        if (disabled.isEmpty()) return null
        return FingerprintBuilder().add(producer.id).add(producer.version).addAll(disabled.sorted()).build()
    }
}
